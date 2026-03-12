package com.arare.features.building;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {
    boolean existsByName(String name);

    /** Remove from department_buildings join table before deleting a building. */
    @Transactional @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM department_buildings WHERE building_id = :id")
    void removeDepartmentAssociations(@Param("id") Long id);

    /** Remove from teacher_preferred_buildings join table before deleting a building. */
    @Transactional @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM teacher_preferred_buildings WHERE building_id = :id")
    void removeTeacherAssociations(@Param("id") Long id);
}
