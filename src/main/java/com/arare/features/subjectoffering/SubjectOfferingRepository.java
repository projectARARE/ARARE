package com.arare.features.subjectoffering;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectOfferingRepository extends JpaRepository<SubjectOffering, Long> {

    @EntityGraph(attributePaths = {"subject", "batch", "batch.department", "section"})
    @Query("SELECT o FROM SubjectOffering o")
    List<SubjectOffering> findAllWithDetails();

    List<SubjectOffering> findByBatchId(Long batchId);

    List<SubjectOffering> findByBatchIdIn(List<Long> batchIds);

    List<SubjectOffering> findBySectionId(Long sectionId);

    List<SubjectOffering> findBySectionIdIn(List<Long> sectionIds);

    List<SubjectOffering> findBySubjectId(Long subjectId);

    boolean existsBySubjectIdAndBatchId(Long subjectId, Long batchId);

    boolean existsBySubjectIdAndSectionId(Long subjectId, Long sectionId);
}