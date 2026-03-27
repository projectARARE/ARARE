package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimetableSolverService {

    private final SolverManager<TimetableSolution, UUID> solverManager;
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;
    private final ScheduleRepository scheduleRepo;
    private final TimetableProblemBuilder problemBuilder;
    private final SolutionPersister solutionPersister;

    @Transactional
    public void solveSchedule(Long scheduleId, Long departmentId,
                              List<Long> batchIds, List<Long> teacherIds, List<Long> roomIds,
                              Integer solvingTimeSeconds) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        TimetableSolution problem = problemBuilder.build(new ProblemBuildRequest(
            schedule,
            null,
            departmentId,
            batchIds,
            teacherIds,
            roomIds
        ));

        TimetableSolution solution = runSolver(problem, solvingTimeSeconds);
        solutionPersister.persist(schedule, solution);
    }

    @Transactional
    public void partialResolve(Long scheduleId, List<Long> impactedSessionIds) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        TimetableSolution problem = problemBuilder.build(new ProblemBuildRequest(
            schedule,
            impactedSessionIds,
            null,
            null,
            null,
            null
        ));

        TimetableSolution solution = runSolver(problem, null);
        solutionPersister.persist(schedule, solution);
    }

    @Transactional(readOnly = true)
    public ScoreExplanationResponse explainSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        TimetableSolution solution = problemBuilder.build(new ProblemBuildRequest(
            schedule,
            null,
            null,
            null,
            null,
            null
        ));

        var explanation = solutionManager.explain(solution);
        HardMediumSoftScore score = explanation.getScore();

        Collection<ConstraintMatchTotal<HardMediumSoftScore>> totals =
            explanation.getConstraintMatchTotalMap().values();

        List<ScoreExplanationResponse.ConstraintBreakdown> breakdowns = totals.stream()
            .filter(t -> !t.getScore().equals(HardMediumSoftScore.ZERO))
            .map(t -> {
                HardMediumSoftScore cs = t.getScore();
                String level = cs.hardScore() != 0 ? "HARD"
                    : cs.mediumScore() != 0 ? "MEDIUM" : "SOFT";
                return new ScoreExplanationResponse.ConstraintBreakdown(
                    t.getConstraintRef().constraintName(),
                    level,
                    t.getConstraintMatchCount(),
                    cs.toString()
                );
            })
            .sorted(java.util.Comparator.comparing(ScoreExplanationResponse.ConstraintBreakdown::level))
            .toList();

        return new ScoreExplanationResponse(
            score != null ? score.toString() : "N/A",
            score != null && score.isFeasible(),
            score != null ? score.hardScore() : 0,
            score != null ? score.mediumScore() : 0,
            score != null ? score.softScore() : 0,
            breakdowns
        );
    }

    @Transactional
    public void persistSolution(Schedule schedule, TimetableSolution solution) {
        solutionPersister.persist(schedule, solution);
    }

    private TimetableSolution runSolver(TimetableSolution problem, Integer solvingTimeSeconds) {
        UUID problemId = UUID.randomUUID();
        int timeLimit = (solvingTimeSeconds != null && solvingTimeSeconds > 0) ? solvingTimeSeconds : 30;
        SolverJob<TimetableSolution, UUID> job = solverManager.solveBuilder()
            .withProblemId(problemId)
            .withProblem(problem)
            .withConfigOverride(new SolverConfigOverride<TimetableSolution>()
                .withTerminationConfig(new TerminationConfig()
                    .withSecondsSpentLimit((long) timeLimit)))
            .run();
        try {
            return job.getFinalBestSolution();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Solver interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Solver execution failed", e.getCause());
        }
    }
}
