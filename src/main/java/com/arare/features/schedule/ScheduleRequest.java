package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request DTO: initiate schedule generation. */
public record ScheduleRequest(
    @NotBlank String name,
    @NotNull ScheduleScope scope,
    /** ID of the schedule to derive from (partial re-optimization). Null = generate from scratch. */
    Long parentScheduleId,
    /** Optional: limit generation to a specific department. */
    Long departmentId
) {}
