package com.arare.features.classsection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {
    List<ClassSection> findByBatchId(Long batchId);
    List<ClassSection> findByBatchIdIn(List<Long> batchIds);

    @Transactional @Modifying
    @Query("DELETE FROM ClassSection cs WHERE cs.batch.id = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);

    @Transactional @Modifying
    @Query("DELETE FROM ClassSection cs WHERE cs.batch.id IN " +
           "(SELECT b.id FROM Batch b WHERE b.department.id = :departmentId)")
    void deleteByDepartmentId(@Param("departmentId") Long departmentId);
}
