package com.arare.features.schedule;

import com.arare.common.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    @EntityGraph(attributePaths = {"parentSchedule", "blockedDays"})
    @Query("SELECT s FROM Schedule s")
    List<Schedule> findAllWithDetails();

    List<Schedule> findByStatus(ScheduleStatus status);

    Optional<Schedule> findTopByStatusOrderByCreatedAtDesc(ScheduleStatus status);

    List<Schedule> findByParentScheduleId(Long parentScheduleId);
}
