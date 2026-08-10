package com.arare.features.solvejob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SolveJobRepository extends JpaRepository<SolveJob, Long> {

    List<SolveJob> findAllByOrderByCreatedAtDesc();

    List<SolveJob> findByStatusOrderByCreatedAtDesc(SolveJobStatus status);

    List<SolveJob> findByStatusIn(Collection<SolveJobStatus> statuses);

    List<SolveJob> findByScheduleIdOrderByCreatedAtDesc(Long scheduleId);

    Optional<SolveJob> findTopByScheduleIdAndJobTypeOrderByCreatedAtDesc(Long scheduleId, SolveJobType jobType);

    long countByScheduleIdAndStatusIn(Long scheduleId, Collection<SolveJobStatus> statuses);
}
