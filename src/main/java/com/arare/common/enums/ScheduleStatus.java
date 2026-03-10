package com.arare.common.enums;

public enum ScheduleStatus {
    /** Schedule is being generated or edited. */
    DRAFT,
    /** Published and in use. */
    ACTIVE,
    /** Superseded by a newer version. */
    ARCHIVED,
    /** Partial result; some constraints could not be satisfied. */
    PARTIAL,
    /** Solver could not find any feasible solution. */
    INFEASIBLE
}
