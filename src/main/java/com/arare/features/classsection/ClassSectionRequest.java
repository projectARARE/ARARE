package com.arare.features.classsection;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request DTO: create or update a ClassSection (lab sub-group). */
public record ClassSectionRequest(
    @NotNull Long batchId,
    @NotNull String label,
    @Min(1) int size
) {}
