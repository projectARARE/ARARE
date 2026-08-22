package com.arare.features.classsection;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// Request DTO: create or update a ClassSection (lab sub-group). 
public record ClassSectionRequest(
    @NotNull Long batchId,
    @NotNull String label,
    @Min(1) int size,
    // Per-section curriculum (which split-lab subjects this section takes).
    // Empty/null = inherit the batch/department curriculum.
    List<Long> subjectIds
) {}
