package com.arare.features.academicterm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {

    boolean existsByName(String name);
}
