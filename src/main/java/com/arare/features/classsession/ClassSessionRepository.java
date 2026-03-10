package com.arare.features.classsession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByScheduleId(Long scheduleId);

    List<ClassSession> findByScheduleIdAndBatchId(Long scheduleId, Long batchId);

    List<ClassSession> findByScheduleIdAndTeacherId(Long scheduleId, Long teacherId);

    List<ClassSession> findByScheduleIdAndRoomId(Long scheduleId, Long roomId);

    /** Sessions that are not yet assigned a timeslot (unsolved). */
    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId AND cs.timeslot IS NULL")
    List<ClassSession> findUnsolvedByScheduleId(@Param("scheduleId") Long scheduleId);

    /** Sessions affected by a teacher absence – not locked and assigned to that teacher. */
    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId " +
           "AND cs.teacher.id = :teacherId AND cs.isLocked = false")
    List<ClassSession> findUnlockedByScheduleIdAndTeacherId(
        @Param("scheduleId") Long scheduleId,
        @Param("teacherId") Long teacherId
    );

    /** Sessions affected by a room becoming unavailable. */
    @Query("SELECT cs FROM ClassSession cs WHERE cs.schedule.id = :scheduleId " +
           "AND cs.room.id = :roomId AND cs.isLocked = false")
    List<ClassSession> findUnlockedByScheduleIdAndRoomId(
        @Param("scheduleId") Long scheduleId,
        @Param("roomId") Long roomId
    );
}
