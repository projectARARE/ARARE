package com.arare.features.classsession;

/** Request DTO for manually reassigning a session's teacher, room, and/or timeslot. */
public record SessionAssignmentRequest(
    Long teacherId,     // null = unassign teacher
    Long roomId,        // null = unassign room
    Long timeslotId,    // null = unassign timeslot
    Boolean locked      // null = keep current lock state
) {}
