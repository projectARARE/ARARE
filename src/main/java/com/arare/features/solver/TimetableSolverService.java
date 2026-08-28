package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.solvejob.SolveJob;
import com.arare.features.solvejob.SolveJobRepository;
import com.arare.features.solvejob.SolveJobType;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solver-facing facade.
 *
 * <p>Solve execution itself lives in the asynchronous solve-job pipeline
 * ({@code SolveJobRunner}); this service only provides read-only analysis
 * (score explanation) and problem construction helpers used by that pipeline.
 */
@Service
@RequiredArgsConstructor
public class TimetableSolverService {

    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;
    private final ScheduleRepository scheduleRepo;
    private final TimetableProblemBuilder problemBuilder;
    private final SolveJobRepository solveJobRepo;

    @Transactional(readOnly = true)
    public ScoreExplanationResponse explainSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));

        // Recompute the explanation with the SAME disruption facts as the last
        // partial-resolve that produced this schedule, so the breakdown matches
        // the persisted score. Without this, an infeasible partial resolve would
        // be re-scored without its disruption constraints and reported feasible.
        List<DisruptionConstraintFact> disruptionFacts = latestPartialResolveFacts(scheduleId);

        // Read-only explain path: do NOT generate/persist sessions for a schedule
        // that has none yet, otherwise this write would fail on a read-only
        // transaction. Passing generateIfMissing=false analyses the existing
        // facts (an empty session set) without writing.
        TimetableSolution solution = problemBuilder.build(new ProblemBuildRequest(
            schedule,
            null,
            null,
            schedule.getInstituteId(),
            null,
            null,
            null,
            disruptionFacts
        ), false);

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

    private List<DisruptionConstraintFact> latestPartialResolveFacts(Long scheduleId) {
        return solveJobRepo.findTopByScheduleIdAndJobTypeOrderByCreatedAtDesc(scheduleId, SolveJobType.PARTIAL_RESOLVE)
            .map(SolveJob::getDisruptionFactsCsv)
            .map(DisruptionConstraintFact::decode)
            .orElseGet(List::of);
    }
}
