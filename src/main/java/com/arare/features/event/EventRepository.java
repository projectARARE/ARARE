package com.arare.features.event;

import com.arare.features.room.Room;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // affectedRooms/Teachers/Timeslots carry @Fetch(SUBSELECT): flat fetch
    // cost (1 + 3 queries) without Hibernate's MultipleBagFetchException.
    @Query("SELECT e FROM Event e")
    List<Event> findAllWithDetails();

    List<Event> findByType(com.arare.common.enums.EventType type);

    // Find active events overlapping with a given date. 
    @Query("SELECT e FROM Event e WHERE e.startDate <= :date AND e.endDate >= :date")
    List<Event> findActiveByDate(@Param("date") LocalDate date);

    // Delete from event_affected_timeslots join table where timeslot_id = :timeslotId
    @Query("DELETE FROM Event e JOIN e.affectedTimeslots ts WHERE ts.id = :timeslotId")
    void deleteEventAffectedTimeslotsByTimeslotId(@Param("timeslotId") Long timeslotId);

    // Delete from event_affected_rooms join table where room_id = :roomId
    @Query("DELETE FROM Event e JOIN e.affectedRooms r WHERE r.id = :roomId")
    void deleteEventAffectedRoomsByRoomId(@Param("roomId") Long roomId);

    // Delete from event_affected_teachers join table where teacher_id = :teacherId
    @Query("DELETE FROM Event e JOIN e.affectedTeachers t WHERE t.id = :teacherId")
    void deleteEventAffectedTeachersByTeacherId(@Param("teacherId") Long teacherId);
}
