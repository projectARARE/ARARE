package com.arare.features.academicterm;

import com.arare.common.enums.AcademicTermStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AcademicTermRequest(
    @NotBlank String name,
    String academicYear,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    LocalDate examPeriodStart,
    LocalDate examPeriodEnd,
    AcademicTermStatus status,
    String description
) {}
