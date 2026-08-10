package com.arare.features.preallocation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PreAllocationRepository extends JpaRepository<PreAllocation, Long> {

    List<PreAllocation> findByScheduleId(Long scheduleId);

    List<PreAllocation> findByScheduleIdAndLocked(Long scheduleId, boolean locked);

    // ─── Cascade-purge helpers (pre_allocations rows must be removed before
    //     their referenced parent rows, which are non-null FKs) ─────────────

    @Transactional @Modifying
    @Query("DELETE FROM PreAllocation pa WHERE pa.schedule.id = :scheduleId")
    void deleteByScheduleId(@Param("scheduleId") Long scheduleId);

    @Transactional @Modifying
    @Query("DELETE FROM PreAllocation pa WHERE pa.batch.id = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);

    @Transactional @Modifying
    @Query("DELETE FROM PreAllocation pa WHERE pa.subject.id = :subjectId")
    void deleteBySubjectId(@Param("subjectId") Long subjectId);

    @Transactional @Modifying
    @Query("DELETE FROM PreAllocation pa WHERE pa.teacher.id = :teacherId")
    void deleteByTeacherId(@Param("teacherId") Long teacherId);

    @Transactional @Modifying
    @Query("DELETE FROM PreAllocation pa WHERE pa.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") Long roomId);

    @Transactional @Modifying
    @Query("DELETE FROM PreAllocation pa WHERE pa.timeslot.id = :timeslotId")
    void deleteByTimeslotId(@Param("timeslotId") Long timeslotId);

    @Transactional @Modifying
    @Query("DELETE FROM PreAllocation pa WHERE " +
           "pa.subject.id IN (SELECT s.id FROM Subject s WHERE s.department.id = :departmentId) OR " +
           "pa.batch.id IN (SELECT b.id FROM Batch b WHERE b.department.id = :departmentId)")
    void deleteByDepartmentId(@Param("departmentId") Long departmentId);
}
