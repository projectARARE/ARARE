package com.arare.features.event;

import com.arare.common.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByType(EventType type);

    /** Find active events overlapping with a given date. */
    @Query("SELECT e FROM Event e WHERE e.startDate <= :date AND e.endDate >= :date")
    List<Event> findActiveByDate(@Param("date") LocalDate date);
}
