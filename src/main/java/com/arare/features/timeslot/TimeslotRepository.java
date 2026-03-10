package com.arare.features.timeslot;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeslotRepository extends JpaRepository<Timeslot, Long> {

    List<Timeslot> findByDay(SchoolDay day);

    List<Timeslot> findByType(TimeslotType type);

    /** Returns all timeslots available for scheduling (not breaks or blocked). */
    List<Timeslot> findByTypeNot(TimeslotType type);

    List<Timeslot> findByDayAndType(SchoolDay day, TimeslotType type);
}
