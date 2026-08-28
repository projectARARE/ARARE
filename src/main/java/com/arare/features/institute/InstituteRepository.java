package com.arare.features.institute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstituteRepository extends JpaRepository<Institute, Long> {
    Optional<Institute> findByName(String name);

    Optional<Institute> findByCode(String code);

    // Find all institutes ordered by name for stable dropdowns/lists.
    List<Institute> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    boolean existsByCode(String code);
}
