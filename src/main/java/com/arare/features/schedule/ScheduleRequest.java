package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Request DTO: initiate schedule generation. */
public record ScheduleRequest(
    @NotBlank String name,
    @NotNull ScheduleScope scope,
    /** ID of the schedule to derive from (partial re-optimization). Null = generate from scratch. */
    Long parentScheduleId,
    /** Optional: limit generation to a specific department. */
    Long departmentId,
    /** Builder mode: restrict to these batch IDs only. Null/empty = include all. */
    List<Long> batchIds,
    /** Builder mode: restrict to these teacher IDs only. Null/empty = include all. */
    List<Long> teacherIds,
    /** Builder mode: restrict to these room IDs only. Null/empty = include all. */
    List<Long> roomIds,
    /** Solving time limit in seconds. Null = use default (30s). */
    Integer solvingTimeSeconds
) {}
