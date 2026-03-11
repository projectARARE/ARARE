package com.arare.features.solver;

import java.util.List;

/**
 * Structured breakdown of the current schedule score, suitable for the UI.
 *
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
        String level,       // HARD / MEDIUM / SOFT
        int matchCount,
        String scoreImpact  // e.g. "-3hard"
    ) {}
}
