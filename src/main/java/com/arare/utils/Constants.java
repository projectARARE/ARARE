package com.arare.utils;

/**
 * Application-wide constants.
 */
public final class Constants {

    private Constants() { /* utility class */ }

    /** Penalty weights for minimal-change re-optimization (spec §7). */
    public static final int DISRUPTION_PENALTY_ROOM_CHANGE     = 2;
    public static final int DISRUPTION_PENALTY_TEACHER_CHANGE  = 5;
    public static final int DISRUPTION_PENALTY_TIMESLOT_CHANGE = 10;

    /** Default student cognitive load cap per day (spec §5). */
    public static final int DEFAULT_MAX_SAME_SUBJECT_PER_DAY = 1;

    /** API version prefix. */
    public static final String API_V1 = "/api/v1";
}
