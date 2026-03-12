package com.arare.features.academicterm;

import java.util.List;

public interface AcademicTermService {
    List<AcademicTermResponse> findAll();
    AcademicTermResponse findById(Long id);
    AcademicTermResponse create(AcademicTermRequest request);
    AcademicTermResponse update(Long id, AcademicTermRequest request);
    void delete(Long id);
}
