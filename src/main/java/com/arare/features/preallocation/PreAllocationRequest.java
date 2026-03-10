package com.arare.features.preallocation;

import jakarta.validation.constraints.NotNull;

/** Request DTO: lock a specific assignment before solving. */
public record PreAllocationRequest(
    @NotNull Long scheduleId,
    @NotNull Long batchId,
    @NotNull Long subjectId,
    Long teacherId,
    Long roomId,
    @NotNull Long timeslotId,
    boolean locked
) {}
