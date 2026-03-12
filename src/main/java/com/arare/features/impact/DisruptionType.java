package com.arare.features.impact;

public enum DisruptionType {
    /** A teacher is absent or unavailable for a given day/period. */
    TEACHER_UNAVAILABLE,
    /** A room is closed, under maintenance, or otherwise unusable. */
    ROOM_UNAVAILABLE,
    /** A specific timeslot is blocked (e.g. assembly, special event). */
    TIMESLOT_BLOCKED,
    /** A specific session must be cancelled outright. */
    SESSION_CANCELLED,
    /** A special event occupies a block of time (multi-type disruption). */
    SPECIAL_EVENT
}
