package com.arare.features.teacherassignment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {

    @EntityGraph(attributePaths = {"teacher", "subject", "batch", "batch.department", "section"})
    @Query("SELECT a FROM TeacherAssignment a")
    List<TeacherAssignment> findAllWithDetails();

    @EntityGraph(attributePaths = {"teacher", "subject", "batch", "batch.department", "section"})
    @Query("SELECT a FROM TeacherAssignment a WHERE a.teacher.id = :teacherId")
    List<TeacherAssignment> findByTeacherIdWithDetails(Long teacherId);

    List<TeacherAssignment> findByTeacherId(Long teacherId);

    List<TeacherAssignment> findByTeacherIdIn(Collection<Long> teacherIds);

    List<TeacherAssignment> findByBatchId(Long batchId);

    List<TeacherAssignment> findByBatchIdIn(Collection<Long> batchIds);

    List<TeacherAssignment> findBySectionIdIn(Collection<Long> sectionIds);

    List<TeacherAssignment> findBySubjectId(Long subjectId);
}