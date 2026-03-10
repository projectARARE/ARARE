package com.arare.features.teacher;

import com.arare.common.enums.SchoolDay;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Request DTO: create or update a Teacher. */
public record TeacherRequest(
    @NotBlank String name,
    List<Long> subjectIds,
    List<Long> availableTimeslotIds,
    List<Long> preferredBuildingIds,
    @Min(1) int maxDailyHours,
    @Min(1) int maxWeeklyHours,
    @Min(1) int maxConsecutiveClasses,
    int movementPenalty,
    SchoolDay preferredFreeDay
) {}
