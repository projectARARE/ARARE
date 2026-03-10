package com.arare.features.universityconfig;

import com.arare.common.enums.SchoolDay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/** Request DTO: configure global university scheduling parameters. */
public record UniversityConfigRequest(
    @Min(5) @Max(6) int daysPerWeek,
    @Min(1) int timeslotsPerDay,
    @Min(1) int maxClassesPerDay,
    List<Integer> breakSlotIndices,
    List<SchoolDay> workingDays
) {}
