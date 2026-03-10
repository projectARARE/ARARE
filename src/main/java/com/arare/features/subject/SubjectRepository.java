package com.arare.features.subject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByDepartmentId(Long departmentId);

    List<Subject> findByIsLab(boolean isLab);

    List<Subject> findByDepartmentIdAndIsLab(Long departmentId, boolean isLab);
}
