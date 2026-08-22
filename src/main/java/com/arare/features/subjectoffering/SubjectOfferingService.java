package com.arare.features.subjectoffering;

import java.util.List;

public interface SubjectOfferingService {

    SubjectOfferingResponse create(SubjectOfferingRequest req);

    SubjectOfferingResponse update(Long id, SubjectOfferingRequest req);

    SubjectOfferingResponse findById(Long id);

    List<SubjectOfferingResponse> findAll();

    List<SubjectOfferingResponse> findByBatch(Long batchId);

    List<SubjectOfferingResponse> findBySection(Long sectionId);

    List<SubjectOfferingResponse> findBySubject(Long subjectId);

    void delete(Long id);
}