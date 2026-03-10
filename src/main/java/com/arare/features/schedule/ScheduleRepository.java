package com.arare.features.schedule;

import com.arare.common.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByStatus(ScheduleStatus status);

    Optional<Schedule> findTopByStatusOrderByCreatedAtDesc(ScheduleStatus status);

    List<Schedule> findByParentScheduleId(Long parentScheduleId);
}
