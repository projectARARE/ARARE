package com.arare.features.preallocation;

import java.util.List;

public interface PreAllocationService {
    PreAllocationResponse create(PreAllocationRequest request);
    PreAllocationResponse findById(Long id);
    List<PreAllocationResponse> findBySchedule(Long scheduleId);
    List<PreAllocationResponse> findAll();
    void delete(Long id);
    List<PreAllocationResponse> createAll(Long scheduleId, List<PreAllocationSpec> specs);
}