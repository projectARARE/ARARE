package com.arare.features.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// Request DTO: create or update a Department.
public record DepartmentRequest(
    @NotBlank String name,
    @NotBlank String code,
    @NotNull Long instituteId,
    List<Long> buildingIds
) {}
