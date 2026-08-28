package com.arare.features.batch;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    // subjects fetched via @Fetch(SUBSELECT); workingDays (bag) + to-one graph
    // are joined eagerly. No multiple-bag fetch is triggered.
    @EntityGraph(attributePaths = {"department", "department.institute", "homeRoom", "workingDays"})
    @Query("SELECT b FROM Batch b")
    List<Batch> findAllWithDetails();

    @EntityGraph(attributePaths = {"department", "department.institute", "homeRoom", "workingDays"})
    @Query("SELECT b FROM Batch b WHERE b.department.id = :departmentId")
    List<Batch> findByDepartmentIdWithDetails(@Param("departmentId") Long departmentId);

    @EntityGraph(attributePaths = {"department", "department.institute", "homeRoom", "workingDays"})
    @Query("SELECT b FROM Batch b WHERE b.department.institute.id = :instituteId")
    List<Batch> findByDepartmentInstituteIdWithDetails(@Param("instituteId") Long instituteId);

    List<Batch> findByDepartmentId(Long departmentId);

    @Query("SELECT b FROM Batch b WHERE b.department.institute.id = :instituteId")
    List<Batch> findByDepartmentInstituteId(@Param("instituteId") Long instituteId);

    List<Batch> findByDepartmentIdAndYear(Long departmentId, int year);

    boolean existsByDepartmentIdAndYearAndSection(Long departmentId, int year, String section);

    @Transactional @Modifying
    @Query("DELETE FROM Batch b WHERE b.department.id = :departmentId")
    void deleteByDepartmentId(@Param("departmentId") Long departmentId);

    // Null out Batch.homeRoom before a Room (or its Building) is deleted, since
    // Batch.homeRoom is a plain @ManyToOne with no cascade/@OnDelete.
    @Transactional @Modifying
    @Query("UPDATE Batch b SET b.homeRoom = null WHERE b.homeRoom.id = :roomId")
    void clearHomeRoomByRoomId(@Param("roomId") Long roomId);

    @Transactional @Modifying
    @Query("UPDATE Batch b SET b.homeRoom = null WHERE b.homeRoom IS NOT NULL AND b.homeRoom.building.id = :buildingId")
    void clearHomeRoomByBuildingId(@Param("buildingId") Long buildingId);
}
