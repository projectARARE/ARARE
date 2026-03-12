package com.arare.features.classsession;

import java.util.List;

public interface ClassSessionService {
    List<ClassSessionResponse> findBySchedule(Long scheduleId);
    List<ClassSessionResponse> findByScheduleAndBatch(Long scheduleId, Long batchId);
    List<ClassSessionResponse> findByScheduleAndTeacher(Long scheduleId, Long teacherId);
    /** Manually override teacher/room/timeslot for a session and toggle its lock. */
    ClassSessionResponse updateAssignment(Long sessionId, SessionAssignmentRequest req);
}
