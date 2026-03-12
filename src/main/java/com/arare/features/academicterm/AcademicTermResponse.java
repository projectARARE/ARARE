package com.arare.features.academicterm;

import com.arare.common.enums.AcademicTermStatus;
import java.time.LocalDate;

public record AcademicTermResponse(
    Long id,
    String name,
    String academicYear,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate examPeriodStart,
    LocalDate examPeriodEnd,
    AcademicTermStatus status,
    String description,
    String createdAt
) {}
