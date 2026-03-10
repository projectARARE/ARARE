package com.arare.features.batch;

import com.arare.common.enums.SchoolDay;
import java.util.List;

public record BatchResponse(
    Long id,
    Long departmentId,
    String departmentName,
    int year,
    String section,
    int studentCount,
    List<SchoolDay> workingDays,
    SchoolDay preferredFreeDay
) {}
