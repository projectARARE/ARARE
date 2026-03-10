package com.arare.features.preallocation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreAllocationRepository extends JpaRepository<PreAllocation, Long> {

    List<PreAllocation> findByScheduleId(Long scheduleId);

    List<PreAllocation> findByScheduleIdAndLocked(Long scheduleId, boolean locked);
}
