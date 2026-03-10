package com.arare.features.batch;

import java.util.List;

public interface BatchService {
    BatchResponse create(BatchRequest request);
    BatchResponse update(Long id, BatchRequest request);
    BatchResponse findById(Long id);
    List<BatchResponse> findAll();
    List<BatchResponse> findByDepartment(Long departmentId);
    void delete(Long id);
}
