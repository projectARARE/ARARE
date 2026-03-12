package com.arare.features.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByDepartmentId(Long departmentId);

    List<Batch> findByDepartmentIdAndYear(Long departmentId, int year);

    @Transactional @Modifying
    @Query("DELETE FROM Batch b WHERE b.department.id = :departmentId")
    void deleteByDepartmentId(@Param("departmentId") Long departmentId);
}
