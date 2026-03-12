package com.arare.features.teacher;

import com.arare.common.enums.SchoolDay;
import java.util.List;

public record TeacherResponse(
    Long id,
    String name,
    List<Long> subjectIds,
    List<String> subjectNames,
    List<Long> availableTimeslotIds,
    List<Long> preferredBuildingIds,
    int maxDailyHours,
    int maxWeeklyHours,
    int maxConsecutiveClasses,
    int movementPenalty,
    SchoolDay preferredFreeDay
) {}
