package com.arare.features.classsection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Request DTO: generate a run of class sections for a batch in one call.
// For a batch like "CSE" with {@code count = 15} and {@code prefix = "CSE"},
// this creates CSE1, CSE2, ... CSE15 automatically, so a teacher can later be
// allotted to only a specific subset of them (e.g. CSE1..CSE3).
public record ClassSectionBulkRequest(
    @NotNull Long batchId,
    @NotBlank String prefix,
    @Min(1) @Max(100) int count,
    @Min(1) int size
) {}
