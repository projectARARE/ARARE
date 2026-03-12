package com.arare.common.enums;

public enum AcademicTermStatus {
    /** Term is defined but not yet started. */
    UPCOMING,
    /** Currently active term. */
    ACTIVE,
    /** Term has ended; schedules are read-only. */
    CLOSED,
    /** Archived for historical reference. */
    ARCHIVED
}
