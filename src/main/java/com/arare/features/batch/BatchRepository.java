package com.arare.features.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByDepartmentId(Long departmentId);

    List<Batch> findByDepartmentIdAndYear(Long departmentId, int year);
}
