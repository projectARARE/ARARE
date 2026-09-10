package com.arare.features.solver;

import java.util.List;

/**
 * Structured breakdown of the current schedule score, suitable for the UI.
 * <p>Each {@link ConstraintBreakdown} entry names one constraint and shows
 * how many planning entities violated it and the composite score penalty.</p>
 */
public record ScoreExplanationResponse(
    String score,
    boolean feasible,
    int hardScore,
    int mediumScore,
    int softScore,
    List<ConstraintBreakdown> constraints
) {

    public record ConstraintBreakdown(
        String constraintName,
        /**
         * HARD / MEDIUM / SOFT
         */
        String level,       
        int matchCount,
        /**
         * e.g. "-3hard"
         */
        String scoreImpact,
        /**
         * Ids of the ClassSession planning entities implicated in this
         * constraint, so the UI can pinpoint and highlight them.
         */
        List<Long> sessionIds
    ) {}
}
