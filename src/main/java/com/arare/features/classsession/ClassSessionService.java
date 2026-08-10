package com.arare.features.classsession;

import java.util.List;

public interface ClassSessionService {
    List<ClassSessionResponse> findBySchedule(Long scheduleId);
    List<ClassSessionResponse> findByScheduleAndBatch(Long scheduleId, Long batchId);
    List<ClassSessionResponse> findByScheduleAndTeacher(Long scheduleId, Long teacherId);
    // Manually override teacher/room/timeslot for a session and toggle its lock. 
    ClassSessionResponse updateAssignment(Long sessionId, SessionAssignmentRequest req);
    // Manually create a brand-new session (subject + batch/section + optional
    // assignment) and persist it against a schedule.
    ClassSessionResponse create(SessionCreateRequest req);
}
