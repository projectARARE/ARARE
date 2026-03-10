package com.arare.features.preallocation;

import java.util.List;

public interface PreAllocationService {
    PreAllocationResponse create(PreAllocationRequest request);
    PreAllocationResponse findById(Long id);
    List<PreAllocationResponse> findBySchedule(Long scheduleId);
    void delete(Long id);
}
