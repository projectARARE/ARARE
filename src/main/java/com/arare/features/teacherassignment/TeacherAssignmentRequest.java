package com.arare.features.teacherassignment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Payload for creating/updating a term teaching allotment. Both batch and
// section may be provided, but exactly one must be set (service-enforced).
public record TeacherAssignmentRequest(
    @NotNull Long teacherId,
    @NotNull Long subjectId,
    Long batchId,
    Long sectionId,
    @Min(1) Integer weeklyHours,
    @Min(0) Integer priority,
    @Size(max = 400) String notes
) {
}