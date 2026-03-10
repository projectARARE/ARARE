package com.arare.features.batch;

import com.arare.common.enums.SchoolDay;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Request DTO: create or update a Batch (student cohort). */
public record BatchRequest(
    @NotNull Long departmentId,
    @Min(1) int year,
    @NotNull String section,
    @Min(1) int studentCount,
    List<SchoolDay> workingDays,
    SchoolDay preferredFreeDay
) {}
