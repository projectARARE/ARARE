package com.arare.features.preallocation;

// A pre-allocation specified by the Schedule Generator wizard before the
// schedule exists. Unlike {@link PreAllocationRequest} it carries no scheduleId
// (the schedule row is created at generate time); the service layer binds
// these specs to the freshly created schedule.
// <p>{@code timeslotId} is optional — when null only the teacher (and
// optionally the room) are pinned and the solver picks a compatible slot.</p>
public record PreAllocationSpec(
    Long batchId,
    Long subjectId,
    Long teacherId,
    Long roomId,
    Long timeslotId
) {}