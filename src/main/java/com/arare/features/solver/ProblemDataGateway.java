package com.arare.features.solver;

import com.arare.features.classsession.ClassSession;
import com.arare.features.preallocation.PreAllocation;
import com.arare.features.teacherassignment.TeacherAssignment;
import java.util.List;

public interface ProblemDataGateway {

    ProblemFacts loadFacts(ProblemBuildRequest request);

    List<ClassSession> findSessionsByScheduleId(Long scheduleId);

    List<ClassSession> saveSessions(List<ClassSession> sessions);

    List<ClassSession> findLockedParentSessions(Long parentScheduleId);

    List<PreAllocation> findLockedPreAllocations(Long scheduleId);

    // Term teacher allotments scoped to the batches/sections in the problem.
    // Used to derive each session's allowedTeacherIds (teacherNotAssignedToClass).
    List<TeacherAssignment> loadAssignments(List<Long> batchIds, List<Long> sectionIds);

    // (teacher, day, slot-range) already claimed in OTHER ACTIVE schedules.
    // instituteId (nullable) restricts the scan to schedules of that
    // institute; null scans university-wide for shared teachers.
    List<TeacherBusyInterval> findTeacherBusyIntervals(Long scheduleId, Long instituteId, List<Long> teacherIds);

    // Room equivalents of the teacher busy-interval scan: the same (room, day,
    // slot) combination already used in another ACTIVE schedule. instituteId
    // scopes the scan exactly like the teacher query.
    List<RoomBusyInterval> findRoomBusyIntervals(Long scheduleId, Long instituteId, List<Long> roomIds);

    // Assignments from a parent schedule, keyed by PreviousAssignment.keyFor,
    // used by the soft minimizeMovedSessions constraint on regenerate.
    List<PreviousAssignment> findPreviousAssignments(Long parentScheduleId);
}
