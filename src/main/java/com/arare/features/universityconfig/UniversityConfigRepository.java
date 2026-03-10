package com.arare.features.universityconfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UniversityConfigRepository extends JpaRepository<UniversityConfig, Long> {
    Optional<UniversityConfig> findByActiveTrue();
}
