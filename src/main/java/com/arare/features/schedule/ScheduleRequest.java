package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.SchoolDay;
import com.arare.features.preallocation.PreAllocationSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ScheduleRequest(
    @NotBlank String name,
    @NotNull ScheduleScope scope,
    Long parentScheduleId,
    Long departmentId,
    Long instituteId,
    List<Long> batchIds,
    List<Long> teacherIds,
    List<Long> roomIds,
    Integer solvingTimeSeconds,
    List<SchoolDay> blockedDays,
    // Pre-assignments made in the wizard BEFORE the schedule row exists. The
    // service persists them at generate time; a spec WITHOUT a timeslotId pins
    // only the teacher (and optionally room) and the solver picks the slot.
    List<PreAllocationSpec> preAllocations
) {}
