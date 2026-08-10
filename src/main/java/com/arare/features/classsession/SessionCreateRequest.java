package com.arare.features.classsession;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Request DTO for manually adding a brand-new lecture/lab session to a
// schedule (e.g. right-click "add session here" in the timetable grid).
// {@code batchId} XOR {@code sectionId} must be provided; the entity
// invariant is enforced by ClassSession.validateInvariant on save.
public record SessionCreateRequest(
    @NotNull Long scheduleId,
    @NotNull Long subjectId,
    Long batchId,
    Long sectionId,
    Long teacherId,
    Long roomId,
    Long timeslotId,
    @Min(1)
    Integer duration,   // null = inherit subject.chunkHours
    Boolean locked
) {}