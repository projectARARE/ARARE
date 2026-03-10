package com.arare.features.department;

import java.util.List;

public interface DepartmentService {
    DepartmentResponse create(DepartmentRequest request);
    DepartmentResponse update(Long id, DepartmentRequest request);
    DepartmentResponse findById(Long id);
    List<DepartmentResponse> findAll();
    void delete(Long id);
}
