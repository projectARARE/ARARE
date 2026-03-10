package com.arare.features.teacher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /** Find teachers qualified for a given subject. */
    @Query("SELECT t FROM Teacher t JOIN t.subjects s WHERE s.id = :subjectId")
    List<Teacher> findBySubjectId(@Param("subjectId") Long subjectId);

    /** Find teachers who prefer a given building. */
    @Query("SELECT t FROM Teacher t JOIN t.preferredBuildings b WHERE b.id = :buildingId")
    List<Teacher> findByPreferredBuildingId(@Param("buildingId") Long buildingId);
}
