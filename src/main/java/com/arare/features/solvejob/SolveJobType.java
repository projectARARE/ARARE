package com.arare.features.solvejob;

public enum SolveJobType {
    // Full schedule generation (creates a DRAFT schedule and solves it).
    GENERATE,
    // Re-solve only the impacted sessions of an existing schedule.
    PARTIAL_RESOLVE
}
