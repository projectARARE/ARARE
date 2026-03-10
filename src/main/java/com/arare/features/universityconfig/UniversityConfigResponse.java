package com.arare.features.universityconfig;

import com.arare.common.enums.SchoolDay;
import java.util.List;

public record UniversityConfigResponse(
    Long id,
    boolean active,
    int daysPerWeek,
    int timeslotsPerDay,
    int maxClassesPerDay,
    List<Integer> breakSlotIndices,
    List<SchoolDay> workingDays
) {}
