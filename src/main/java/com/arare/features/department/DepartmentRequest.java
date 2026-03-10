package com.arare.features.department;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Request DTO: create or update a Department. */
public record DepartmentRequest(
    @NotBlank String name,
    List<Long> buildingIds
) {}
