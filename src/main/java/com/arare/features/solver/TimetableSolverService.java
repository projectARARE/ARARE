package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
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
}
