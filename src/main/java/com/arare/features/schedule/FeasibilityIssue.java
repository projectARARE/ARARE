package com.arare.features.schedule;

/**
 * A single validation finding produced by {@link FeasibilityCheckService}.
 *
 * @param severity   ERROR = solver will definitely fail; WARNING = solver may struggle.
 * @param category   Broad category: BATCH, SUBJECT, TEACHER, ROOM, TIMESLOT, CAPACITY.
 * @param message    Human-readable explanation shown in the UI.
 * @param entityId   Optional ID of the offending entity (for deep-linking in the UI).
 * @param entityName Optional display name of the offending entity.
 */
public record FeasibilityIssue(
        Severity severity,
        String   category,
        String   message,
        Long     entityId,
        String   entityName
) {
    public enum Severity { ERROR, WARNING }
}
