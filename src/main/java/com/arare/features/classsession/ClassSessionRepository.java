package com.arare.features.classsession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByScheduleId(Long scheduleId);

    List<ClassSession> findByScheduleIdAndBatchId(Long scheduleId, Long batchId);

    List<ClassSession> findByScheduleIdAndTeacherId(Long scheduleId, Long teacherId);

    List<ClassSession> findByScheduleIdAndRoomId(Long scheduleId, Long roomId);

    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId AND cs.timeslot IS NULL")
    List<ClassSession> findUnsolvedByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId " +
           "AND cs.teacher.id = :teacherId AND cs.isLocked = false")
    List<ClassSession> findUnlockedByScheduleIdAndTeacherId(
        @Param("scheduleId") Long scheduleId,
        @Param("teacherId") Long teacherId
    );

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
    @Query("UPDATE ClassSession cs SET cs.room = null " +
           "WHERE cs.room.id IN (SELECT r.id FROM Room r WHERE r.building.id = :buildingId)")
    void clearRoomsByBuildingId(@Param("buildingId") Long buildingId);
}
