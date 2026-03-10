package com.arare.features.subject;

import java.util.List;

public interface SubjectService {
    SubjectResponse create(SubjectRequest request);
    SubjectResponse update(Long id, SubjectRequest request);
    SubjectResponse findById(Long id);
    List<SubjectResponse> findAll();
    List<SubjectResponse> findByDepartment(Long departmentId);
    void delete(Long id);
}
