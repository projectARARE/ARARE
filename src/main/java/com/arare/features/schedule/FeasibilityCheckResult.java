package com.arare.features.schedule;

import java.util.List;

/**
 * Aggregated result of the pre-solve feasibility check.
 *
 * @param feasible               False if any ERROR-level issue is present.
 * @param errorCount             Number of hard errors (solver will definitely fail).
 * @param warningCount           Number of warnings (solver may produce poor results).
 * @param totalSessionsEstimate  Estimated number of ClassSessions the solver will create.
 * @param availableTimeslots     Number of CLASS-type timeslots currently configured.
 * @param issues                 Ordered list of all findings (errors first, then warnings).
 */
public record FeasibilityCheckResult(
        boolean              feasible,
        int                  errorCount,
        int                  warningCount,
        int                  totalSessionsEstimate,
        int                  availableTimeslots,
        List<FeasibilityIssue> issues
) {}
