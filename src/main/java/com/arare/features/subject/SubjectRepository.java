package com.arare.features.subject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByDepartmentId(Long departmentId);

    @Query("SELECT s FROM Subject s WHERE s.isLab = :isLab")
    List<Subject> findByIsLab(@Param("isLab") boolean isLab);

    @Query("SELECT s FROM Subject s WHERE s.department.id = :departmentId AND s.isLab = :isLab")
    List<Subject> findByDepartmentIdAndIsLab(
        @Param("departmentId") Long departmentId,
        @Param("isLab") boolean isLab
    );

    @Transactional @Modifying
    @Query("DELETE FROM Subject s WHERE s.department.id = :departmentId")
    void deleteByDepartmentId(@Param("departmentId") Long departmentId);

    /** Remove one subject from teacher_subjects join table before single-subject deletion. */
    @Transactional @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM teacher_subjects WHERE subject_id = :id")
    void removeTeacherAssociations(@Param("id") Long id);

    /** Bulk-remove all subjects of a department from the teacher_subjects join table. */
    @Transactional @Modifying
    @Query(nativeQuery = true,
           value = "DELETE FROM teacher_subjects WHERE subject_id IN " +
                   "(SELECT id FROM subjects WHERE department_id = :departmentId)")
    void removeTeacherAssociationsByDepartment(@Param("departmentId") Long departmentId);
}
