package com.arare.features.classsection;

import java.util.List;

public interface ClassSectionService {
    ClassSectionResponse create(ClassSectionRequest request);
    ClassSectionResponse update(Long id, ClassSectionRequest request);
    ClassSectionResponse findById(Long id);
    List<ClassSectionResponse> findByBatch(Long batchId);
    void delete(Long id);
}
