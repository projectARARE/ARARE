package com.arare.features.teacher;

import java.util.List;

public interface TeacherService {
    TeacherResponse create(TeacherRequest request);
    TeacherResponse update(Long id, TeacherRequest request);
    TeacherResponse findById(Long id);
    List<TeacherResponse> findAll();
    void delete(Long id);
}
