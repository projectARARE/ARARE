package com.arare.features.universityconfig;

import java.util.List;
import java.util.Map;

public record UniversityConfigDiagnosticsResponse(
    boolean valid,
    String summary,
    Integer daysPerWeek,
    Integer timeslotsPerDay,
    Integer maxClassesPerDay,
    List<String> workingDays,
    Map<String, Integer> classSlotsPerDay,
    List<String> issues
) {}
