package com.arare.features.solvejob;

public enum SolveJobStatus {
    // Submitted but not yet picked up by a solver worker.
    QUEUED,
    // Solver is actively working on the problem.
    RUNNING,
    // Solve finished and the solution was persisted.
    SUCCEEDED,
    // Solve failed (build error, infeasible result, unexpected exception).
    FAILED,
    // Operator requested early termination.
    CANCELLED
}
