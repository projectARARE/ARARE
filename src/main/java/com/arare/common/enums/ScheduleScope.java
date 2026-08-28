package com.arare.common.enums;

public enum ScheduleScope {
    // Single department only.
    DEPARTMENT,
    // Multiple departments within one institute (college); shared rooms allowed.
    INSTITUTE,
    // Multiple institutes; shared buildings and rooms.
    UNIVERSITY
}
