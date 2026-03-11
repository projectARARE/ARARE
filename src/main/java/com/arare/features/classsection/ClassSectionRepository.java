package com.arare.features.classsection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {
    List<ClassSection> findByBatchId(Long batchId);
    List<ClassSection> findByBatchIdIn(List<Long> batchIds);
}
