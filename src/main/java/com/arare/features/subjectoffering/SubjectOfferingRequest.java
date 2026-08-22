package com.arare.features.subjectoffering;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Request DTO: create or update a SubjectOffering.
public record SubjectOfferingRequest(
    @NotNull Long subjectId,
    Long batchId,
    Long sectionId,
    @Min(1) Integer weeklyHours,
    boolean elective
) {}