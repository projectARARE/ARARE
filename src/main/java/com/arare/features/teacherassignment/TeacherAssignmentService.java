package com.arare.features.teacherassignment;

import java.util.List;

public interface TeacherAssignmentService {

    TeacherAssignmentResponse create(TeacherAssignmentRequest request);

    TeacherAssignmentResponse update(Long id, TeacherAssignmentRequest request);

    TeacherAssignmentResponse findById(Long id);

    List<TeacherAssignmentResponse> findAll();

    List<TeacherAssignmentResponse> findByTeacher(Long teacherId);

    List<TeacherAssignmentResponse> findByBatch(Long batchId);

    List<TeacherAssignmentResponse> findBySubject(Long subjectId);

    void delete(Long id);
}