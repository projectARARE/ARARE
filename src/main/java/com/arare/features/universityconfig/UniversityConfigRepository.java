package com.arare.features.universityconfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UniversityConfigRepository extends JpaRepository<UniversityConfig, Long> {
    Optional<UniversityConfig> findByActiveTrue();

    // Guard against duplicate active configs: the solver problem facts must
    // never carry more than one active config (a second one would double
    // the batchDailyClassesCap penalty).
    Optional<UniversityConfig> findFirstByActiveTrue();
}
