package com.arare.features.institute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Request DTO: create or update an Institute.
public record InstituteRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
    @Size(max = 255) String description
) {}
