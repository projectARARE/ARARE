package com.arare.features.solvejob;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.event.BestSolutionChangedEvent;
import ai.timefold.solver.core.api.solver.event.SolverEventListener;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.solver.ProblemBuildRequest;
import com.arare.features.solver.SolutionPersister;
import com.arare.features.solver.TimetableProblemBuilder;
import com.arare.features.solver.TimetableSolution;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asynchronous solve worker.
 *
 * <p>Deliberately NOT transactional as a whole. Each phase uses its own short
 * transaction or no transaction at all:
 * <ol>
 *   <li>build the Timefold problem inside a read-only transaction,</li>
 *   <li>run the solver with no database connection open,</li>
 *   <li>persist the final solution in a fresh short transaction.</li>
 * </ol>
 * Job status transitions are persisted as individual short transactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolveJobRunner {

    private static final int DEFAULT_SOLVING_TIME_SECONDS = 30;

    private final SolveJobRepository jobRepo;
    private final ScheduleRepository scheduleRepo;
    private final TimetableProblemBuilder problemBuilder;
    private final SolutionPersister solutionPersister;
    private final ActiveSolverRegistry solverRegistry;
    private final TransactionTemplate transactionTemplate;
    private final SolverFactory<TimetableSolution> solverFactory;

    @Async("solveTaskExecutor")
    public void run(Long jobId) {
        SolveJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Solve job {} not found; skipping", jobId);
            return;
        }
        if (job.getStatus() == SolveJobStatus.CANCELLED) {
            return;
        }

        long startedMillis = System.currentTimeMillis();
        job.setStatus(SolveJobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job = jobRepo.save(job);

        UUID problemId = UUID.randomUUID();
        job.setProblemId(problemId);
        final SolveJob workJob = jobRepo.save(job);

        try {
            TimetableSolution problem = transactionTemplate.execute(status -> buildProblem(workJob));
            if (problem == null) {
                throw new IllegalStateException("Failed to build solver problem for job " + jobId);
            }

            int timeLimitSeconds = workJob.getSolvingTimeSeconds() != null && workJob.getSolvingTimeSeconds() > 0
                ? workJob.getSolvingTimeSeconds()
                : DEFAULT_SOLVING_TIME_SECONDS;

            Solver<TimetableSolution> solver = solverFactory.buildSolver(
                new SolverConfigOverride<TimetableSolution>()
                    .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit((long) timeLimitSeconds)));

            solverRegistry.register(problemId, solver);
            solver.addEventListener(new BestScoreListener(workJob, jobRepo));

            TimetableSolution solution = solver.solve(problem);
            long elapsedMillis = System.currentTimeMillis() - startedMillis;

            SolveJob fresh = jobRepo.findById(jobId).orElse(workJob);
            if (fresh.getStatus() == SolveJobStatus.CANCELLED) {
                fresh.setFinishedAt(LocalDateTime.now());
                fresh.setElapsedMillis(elapsedMillis);
                jobRepo.save(fresh);
                log.info("Solve job {} cancelled after {}ms", jobId, elapsedMillis);
                return;
            }

            Schedule schedule = transactionTemplate.execute(status ->
                scheduleRepo.findById(workJob.getScheduleId()).orElse(null));
            if (schedule == null) {
                throw new IllegalStateException("Schedule not found: " + workJob.getScheduleId());
            }

            solutionPersister.persist(schedule, solution);

            fresh.setStatus(SolveJobStatus.SUCCEEDED);
            fresh.setScore(solution.getScore() != null ? solution.getScore().toString() : null);
            fresh.setElapsedMillis(elapsedMillis);
            fresh.setFinishedAt(LocalDateTime.now());
            jobRepo.save(fresh);
            log.info("Solve job {} succeeded in {}ms with score {}", jobId, elapsedMillis, fresh.getScore());
        } catch (IllegalStateException e) {
            markFailed(workJob, startedMillis, e);
        } catch (Exception e) {
            markFailed(workJob, startedMillis, e);
        } finally {
            solverRegistry.unregister(problemId);
        }
    }

    private void markFailed(SolveJob job, long startedMillis, Exception e) {
        long elapsedMillis = System.currentTimeMillis() - startedMillis;
        SolveJob fresh = jobRepo.findById(job.getId()).orElse(job);
        if (fresh.getStatus() == SolveJobStatus.CANCELLED) {
            fresh.setFinishedAt(LocalDateTime.now());
            fresh.setElapsedMillis(elapsedMillis);
            jobRepo.save(fresh);
            return;
        }
        fresh.setStatus(SolveJobStatus.FAILED);
        fresh.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        fresh.setElapsedMillis(elapsedMillis);
        fresh.setFinishedAt(LocalDateTime.now());
        jobRepo.save(fresh);
        log.warn("Solve job {} failed: {}", job.getId(), fresh.getErrorMessage(), e);
    }

    private TimetableSolution buildProblem(SolveJob job) {
        Schedule schedule = scheduleRepo.findById(job.getScheduleId())
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + job.getScheduleId()));

        ProblemBuildRequest request = new ProblemBuildRequest(
            schedule,
            parseIds(job.getImpactedSessionIdsCsv()),
            job.getDepartmentId(),
            parseIds(job.getBatchIdsCsv()),
            parseIds(job.getTeacherIdsCsv()),
            parseIds(job.getRoomIdsCsv())
        );
        return problemBuilder.build(request);
    }

    private static List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Skip malformed tokens in a persisted snapshot; they are
                // never produced by the submit path.
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    /**
     * Best-effort telemetry: persists the latest best score while the solver
     * runs so polling clients see real progress instead of an elapsed-time
     * illusion. Failures are swallowed — this is telemetry, not correctness.
     */
    private static final class BestScoreListener implements SolverEventListener<TimetableSolution> {

private final SolveJobRepository jobRepo;
        private SolveJob job;
        private String lastPersistedScore;

        private BestScoreListener(SolveJob job, SolveJobRepository jobRepo) {
            this.job = job;
            this.jobRepo = jobRepo;
        }

        @Override
        public void bestSolutionChanged(BestSolutionChangedEvent<TimetableSolution> event) {
            TimetableSolution solution = event.getNewBestSolution();
            HardMediumSoftScore score = solution.getScore();
            if (score == null) {
                return;
            }
            String scoreText = score.toString();
            if (scoreText.equals(lastPersistedScore)) {
                return;
            }
            job.setBestScore(scoreText);
            job = jobRepo.save(job);
            lastPersistedScore = job.getBestScore();
        }
    }
}
