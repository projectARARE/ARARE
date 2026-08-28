package com.arare.features.classsession;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    // List endpoints: fetch the association chain (batch.department,
    // section.batch.department, room.building) in one round trip to avoid
    // per-row lazy loads.
    @EntityGraph(attributePaths = {
        "subject", "batch.department", "section.batch.department",
        "teacher", "room.building", "timeslot"
    })
    List<ClassSession> findByScheduleId(Long scheduleId);

    @EntityGraph(attributePaths = {
        "subject", "batch.department", "section.batch.department",
        "teacher", "room.building", "timeslot"
    })
    List<ClassSession> findByScheduleIdAndBatchId(Long scheduleId, Long batchId);

    @EntityGraph(attributePaths = {
        "subject", "batch.department", "section.batch.department",
        "teacher", "room.building", "timeslot"
    })
    List<ClassSession> findByScheduleIdAndTeacherId(Long scheduleId, Long teacherId);

    @EntityGraph(attributePaths = {
        "subject", "batch.department", "section.batch.department",
        "teacher", "room.building", "timeslot"
    })
    List<ClassSession> findByScheduleIdAndRoomId(Long scheduleId, Long roomId);

    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId AND cs.timeslot IS NULL")
    List<ClassSession> findUnsolvedByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId " +
           "AND cs.teacher.id = :teacherId AND cs.isLocked = false")
    List<ClassSession> findUnlockedByScheduleIdAndTeacherId(
        @Param("scheduleId") Long scheduleId,
        @Param("teacherId") Long teacherId
    );

    // ─── Cross-schedule availability (Prompt 3) ─────────────────────────────
    // Sessions that already book a teacher in some OTHER ACTIVE (live)
    // schedule. Both the manual-PATCH gate and the solver busy-interval facts
    // feed off these two queries so a teacher is never double-booked across
    // independently generated timetables.

    @Query("SELECT cs FROM ClassSession cs JOIN cs.schedule sch " +
           "WHERE cs.teacher.id = :teacherId " +
           "AND sch.id <> :excludeScheduleId " +
           "AND sch.status = 'ACTIVE' " +
           "AND (:instituteId IS NULL " +
           "     OR cs.batch.department.institute.id = :instituteId " +
           "     OR cs.section.batch.department.institute.id = :instituteId) " +
           "AND cs.timeslot IS NOT NULL AND cs.teacher IS NOT NULL")
    List<ClassSession> findActiveCrossScheduleSessions(
        @Param("teacherId") Long teacherId,
        @Param("excludeScheduleId") Long excludeScheduleId,
        @Param("instituteId") Long instituteId);

    @Query("SELECT cs.teacher.id, ts.day, ts.slotNumber, ts.startTime, ts.endTime, cs.duration " +
           "FROM ClassSession cs JOIN cs.timeslot ts JOIN cs.schedule sch " +
           "WHERE cs.teacher.id IN :teacherIds " +
           "AND sch.id <> :scheduleId " +
           "AND sch.status = 'ACTIVE' " +
           "AND (:instituteId IS NULL " +
           "     OR cs.batch.department.institute.id = :instituteId " +
           "     OR cs.section.batch.department.institute.id = :instituteId) " +
           "AND cs.timeslot IS NOT NULL AND cs.teacher IS NOT NULL")
    List<Object[]> findActiveCrossScheduleBusyIntervals(
        @Param("scheduleId") Long scheduleId,
        @Param("teacherIds") java.util.Collection<Long> teacherIds,
        @Param("instituteId") Long instituteId);

    @Query("SELECT cs.room.id, ts.day, ts.slotNumber, ts.startTime, ts.endTime, cs.duration " +
           "FROM ClassSession cs JOIN cs.timeslot ts JOIN cs.schedule sch " +
           "WHERE cs.room.id IN :roomIds " +
           "AND sch.id <> :scheduleId " +
           "AND sch.status = 'ACTIVE' " +
           "AND (:instituteId IS NULL " +
           "     OR cs.batch.department.institute.id = :instituteId " +
           "     OR cs.section.batch.department.institute.id = :instituteId) " +
           "AND cs.timeslot IS NOT NULL AND cs.room IS NOT NULL")
    List<Object[]> findActiveCrossScheduleRoomBusyIntervals(
        @Param("scheduleId") Long scheduleId,
        @Param("roomIds") java.util.Collection<Long> roomIds,
        @Param("instituteId") Long instituteId);

    @Query("SELECT cs FROM ClassSession cs JOIN cs.schedule sch " +
           "WHERE cs.room.id = :roomId " +
           "AND sch.id <> :excludeScheduleId " +
           "AND sch.status = 'ACTIVE' " +
           "AND (:instituteId IS NULL " +
           "     OR cs.batch.department.institute.id = :instituteId " +
           "     OR cs.section.batch.department.institute.id = :instituteId) " +
           "AND cs.timeslot IS NOT NULL AND cs.room IS NOT NULL")
    List<ClassSession> findActiveCrossScheduleSessionsByRoom(
        @Param("roomId") Long roomId,
        @Param("excludeScheduleId") Long excludeScheduleId,
        @Param("instituteId") Long instituteId);

    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId " +
           "AND cs.room.id = :roomId AND cs.isLocked = false")
    List<ClassSession> findUnlockedByScheduleIdAndRoomId(
        @Param("scheduleId") Long scheduleId,
        @Param("roomId") Long roomId
    );

    // ─── Cascade-delete helpers ──────────────────────────────────────────────

    @Transactional @Modifying
    @Query("DELETE FROM ClassSession cs WHERE cs.schedule.id = :scheduleId")
    void deleteByScheduleId(@Param("scheduleId") Long scheduleId);

    @Transactional @Modifying
    @Query("DELETE FROM ClassSession cs WHERE cs.batch.id = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);

    @Transactional @Modifying
    @Query("DELETE FROM ClassSession cs WHERE cs.section.id = :sectionId")
    void deleteBySectionId(@Param("sectionId") Long sectionId);

    @Transactional @Modifying
    @Query("DELETE FROM ClassSession cs WHERE cs.subject.id = :subjectId")
    void deleteBySubjectId(@Param("subjectId") Long subjectId);

    @Transactional @Modifying
    @Query("DELETE FROM ClassSession cs WHERE cs.subject.id IN " +
           "(SELECT s.id FROM Subject s WHERE s.department.id = :departmentId)")
    void deleteByDepartmentIdViaSubject(@Param("departmentId") Long departmentId);

    @Transactional @Modifying
    @Query("DELETE FROM ClassSession cs WHERE cs.batch.id IN " +
           "(SELECT b.id FROM Batch b WHERE b.department.id = :departmentId)")
    void deleteByDepartmentIdViaBatch(@Param("departmentId") Long departmentId);

    // ─── SET NULL helpers (keeps sessions, unassigns the planning variable) ──

    @Transactional @Modifying
    @Query("UPDATE ClassSession cs SET cs.teacher = null WHERE cs.teacher.id = :teacherId")
    void clearTeacherById(@Param("teacherId") Long teacherId);

    @Transactional @Modifying
    @Query("UPDATE ClassSession cs SET cs.room = null WHERE cs.room.id = :roomId")
    void clearRoomById(@Param("roomId") Long roomId);

    @Transactional @Modifying
    @Query("UPDATE ClassSession cs SET cs.timeslot = null WHERE cs.timeslot.id = :timeslotId")
    void clearTimeslotById(@Param("timeslotId") Long timeslotId);

    @Transactional @Modifying
    @Query("UPDATE ClassSession cs SET cs.timeslot = null WHERE cs.id = :sessionId")
    void clearTimeslotForSession(@Param("sessionId") Long sessionId);

    @Transactional @Modifying
    @Query("UPDATE ClassSession cs SET cs.room = null " +
           "WHERE cs.room.id IN (SELECT r.id FROM Room r WHERE r.building.id = :buildingId)")
    void clearRoomsByBuildingId(@Param("buildingId") Long buildingId);
    // ��� Single-teacher-per-subject-section guard ����������������������������
    // Serves the manual-edit gate in ClassSessionServiceImpl: finds every
    // OTHER session for the same subject and effective batch (lectures key by
    // cs.batch, section-based labs by cs.section.batch) so a re-assignment
    // cannot introduce a second teacher for that subject.

    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId " +
           "AND cs.subject.id = :subjectId " +
           "AND (cs.batch.id = :effectiveBatchId OR cs.section.batch.id = :effectiveBatchId) " +
           "AND cs.id <> :excludeId")
    List<ClassSession> findSessionsForSubjectAndEffectiveBatch(
        @Param("scheduleId") Long scheduleId,
        @Param("subjectId") Long subjectId,
        @Param("effectiveBatchId") Long effectiveBatchId,
        @Param("excludeId") Long excludeId);
}