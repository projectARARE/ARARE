package com.arare.features.subject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByDepartmentId(Long departmentId);

    /**
     * Explicit JPQL avoids Spring Data's ambiguous "is"-prefix stripping on boolean fields.
     * The JPA field path {@code s.isLab} maps to the {@code is_lab} column directly.
     */
    @Query("SELECT s FROM Subject s WHERE s.isLab = :isLab")
    List<Subject> findByIsLab(@Param("isLab") boolean isLab);

    @Query("SELECT s FROM Subject s WHERE s.department.id = :departmentId AND s.isLab = :isLab")
    List<Subject> findByDepartmentIdAndIsLab(
        @Param("departmentId") Long departmentId,
        @Param("isLab") boolean isLab
    );
}
