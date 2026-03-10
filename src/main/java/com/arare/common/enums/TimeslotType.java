package com.arare.common.enums;

public enum TimeslotType {
    /** Normal teaching period – can be assigned a ClassSession. */
    CLASS,
    /** Fixed break (e.g., lunch). Scheduler must never assign sessions. */
    BREAK,
    /** Manually blocked slot (exam, holiday, event). No sessions allowed. */
    BLOCKED
}
