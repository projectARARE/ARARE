package com.arare.features.impact;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Describes a scheduling disruption to be analyzed by the Impact Analyzer.
 *
 * <p>This is a value object / DTO — it is NOT a JPA entity. The existing
 * {@code Event} entity handles user-visible events; this record drives
 * the dependency graph traversal engine.</p>
 */
public record DisruptionRequest(
    /** What kind of disruption occurred. */
    @NotNull DisruptionType type,
    /** ID of the affected entity (Teacher ID, Room ID, Timeslot ID, or Session ID). */
    @NotNull Long affectedEntityId,
    /**
     * The calendar date of the disruption.
     * For weekly timetables the day-of-week is extracted from this date.
     * Null = affects all days (e.g. room destroyed permanently).
     */
    LocalDate date,
    /** Optional human-readable note shown in the disruption explanation. */
    String description
) {}
