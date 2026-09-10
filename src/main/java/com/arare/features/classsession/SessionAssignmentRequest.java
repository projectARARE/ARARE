package com.arare.features.classsession;

/**
 * Request DTO for manually reassigning a session's teacher, room, and/or timeslot.
 */
public record SessionAssignmentRequest(
    /**
     * null = unassign teacher
     */
    Long teacherId,     
    /**
     * null = unassign room
     */
    Long roomId,        
    /**
     * null = unassign timeslot
     */
    Long timeslotId,    
    /**
     * null = keep current lock state
     */
    Boolean locked,     
    Boolean clearTeacher,
    Boolean clearRoom,
    Boolean clearTimeslot
) {}
