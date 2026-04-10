package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ScheduleRequest(
    @NotBlank String name,
    @NotNull ScheduleScope scope,
    Long parentScheduleId,
    Long departmentId,
    List<Long> batchIds,
    List<Long> teacherIds,
    List<Long> roomIds,
    Integer solvingTimeSeconds
) {}
