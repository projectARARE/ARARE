package com.arare.features.classsession;

import java.util.List;

public interface ClassSessionService {
    List<ClassSessionResponse> findBySchedule(Long scheduleId);
    List<ClassSessionResponse> findByScheduleAndBatch(Long scheduleId, Long batchId);
    List<ClassSessionResponse> findByScheduleAndTeacher(Long scheduleId, Long teacherId);
    /**
     * Manually override teacher/room/timeslot for a session and toggle its lock.
     */
    ClassSessionResponse updateAssignment(Long sessionId, SessionAssignmentRequest req);
    /**
     * Bulk lock-toggle for every session of a schedule (or only the given ids).
     * Returns the number of sessions updated.
     */
    int bulkSetLocked(Long scheduleId, SessionsBulkLockRequest req);
    /**
     * Manually create a brand-new session (subject + batch/section + optional
     * assignment) and persist it against a schedule.
     */
    ClassSessionResponse create(SessionCreateRequest req);
    /**
     * Remove a single session from its schedule.
     */
    void delete(Long sessionId);
}
