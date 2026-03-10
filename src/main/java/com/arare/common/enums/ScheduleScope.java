package com.arare.common.enums;

public enum ScheduleScope {
    /** Single department only. */
    DEPARTMENT,
    /** Multiple departments within one college; shared rooms allowed. */
    COLLEGE,
    /** Multiple colleges; shared buildings and rooms. */
    UNIVERSITY
}
