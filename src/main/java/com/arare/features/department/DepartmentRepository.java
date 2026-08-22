package com.arare.features.department;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByName(String name);

    Optional<Department> findByCode(String code);

    long countByInstituteId(Long instituteId);

    @Query("SELECT d.institute.id, COUNT(d) FROM Department d GROUP BY d.institute.id")
    List<Object[]> countGroupedByInstituteId();

    List<Department> findByInstituteId(Long instituteId);

    // List endpoint: fetch buildingsAllowed in one round trip to avoid 1+N.
    @EntityGraph(attributePaths = {"buildingsAllowed"})
    @Query("SELECT DISTINCT d FROM Department d")
    List<Department> findAllWithBuildings();
}
