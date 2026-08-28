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
import com.arare.features.solver.DisruptionConstraintFact;
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
        UUID problemId = UUID.randomUUID();
        // Guarded transition: only flips QUEUED -> RUNNING (writing problemId)
        // and wins if the job is still QUEUED. A concurrent cancel() issues a
        // guarded terminal UPDATE that leaves the row no longer QUEUED, so this
        // UPDATE matches 0 rows and we bail — the job is never resurrected to
        // RUNNING, and no results are persisted for a job the user cancelled.
        int updated = jobRepo.transitionTerminal(
            jobId,
            List.of(SolveJobStatus.QUEUED),
            SolveJobStatus.RUNNING,
            null,
            null,
            LocalDateTime.now(),
            null,
            problemId);
        if (updated == 0) {
            return; // cancelled/changed before we started
        }
        final SolveJob workJob = jobRepo.findById(jobId).orElse(null);
        if (workJob == null) {
            return;
        }

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

            // Close the cancel/persist race: attempt the QUEUED/RUNNING →
            // SUCCEEDED transition FIRST, inside the same transaction as the
            // persist. If the operator cancelled concurrently, the transition
            // loses (0 rows), so we must not persist — the schedule data would
            // otherwise be written for a job the user just cancelled.
            boolean committed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                int persisted = jobRepo.transitionTerminal(
                    jobId,
                    List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING),
                    SolveJobStatus.SUCCEEDED,
                    solution.getScore() != null ? solution.getScore().toString() : null,
                    elapsedMillis,
                    LocalDateTime.now(),
                    null,
                    workJob.getProblemId());
                if (persisted == 0) {
                    log.info("Solve job {} finished but was cancelled concurrently; result not persisted", jobId);
                    return false;
                }
                solutionPersister.persist(schedule, solution);
                return true;
            }));

            if (committed) {
                log.info("Solve job {} succeeded in {}ms with score {}",
                    jobId, elapsedMillis, solution.getScore());
            }
        } catch (Exception e) {
            markFailed(workJob, startedMillis, e);
        } finally {
            solverRegistry.unregister(problemId);
        }
    }

    private void markFailed(SolveJob job, long startedMillis, Exception e) {
        long elapsedMillis = System.currentTimeMillis() - startedMillis;
        int updated = jobRepo.transitionTerminal(
            job.getId(),
            List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING),
            SolveJobStatus.FAILED,
            null,
            elapsedMillis,
            LocalDateTime.now(),
            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
            null);
        if (updated == 0) {
            SolveJob fresh = jobRepo.findById(job.getId()).orElse(job);
            if (fresh.getStatus() == SolveJobStatus.CANCELLED) {
                log.info("Solve job {} failed after cancellation; status kept as {}", job.getId(), fresh.getStatus());
            } else {
                log.warn("Solve job {} failed but could not transition (status {}): {}",
                    job.getId(), fresh.getStatus(), e.getMessage(), e);
            }
            return;
        }
        log.warn("Solve job {} failed: {}", job.getId(), e.getMessage(), e);
    }

    private TimetableSolution buildProblem(SolveJob job) {
        Schedule schedule = scheduleRepo.findById(job.getScheduleId())
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + job.getScheduleId()));

        ProblemBuildRequest request = new ProblemBuildRequest(
            schedule,
            parseIds(job.getImpactedSessionIdsCsv()),
            job.getDepartmentId(),
            job.getInstituteId(),
            parseIds(job.getBatchIdsCsv()),
            parseIds(job.getTeacherIdsCsv()),
            parseIds(job.getRoomIdsCsv()),
            DisruptionConstraintFact.decode(job.getDisruptionFactsCsv())
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

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BestScoreListener.class);

        private final SolveJobRepository jobRepo;
        private final Long jobId;
        private String lastPersistedScore;

        private BestScoreListener(SolveJob job, SolveJobRepository jobRepo) {
            this.jobRepo = jobRepo;
            this.jobId = job.getId();
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
            try {
                // Guarded update: only applies while the job is QUEUED/RUNNING.
                // Never merges the detached entity, so a concurrent cancel can
                // never be resurrected to RUNNING by a stale telemetry write.
                jobRepo.updateBestScoreIfActive(jobId, scoreText);
                lastPersistedScore = scoreText;
            } catch (RuntimeException ex) {
                // Telemetry only: a transient DB failure must never abort the
                // solve. The next best-score change will retry the save.
                log.warn("Failed to persist best score {} for job {}: {}",
                    scoreText, jobId, ex.getMessage());
            }
        }
    }
}
