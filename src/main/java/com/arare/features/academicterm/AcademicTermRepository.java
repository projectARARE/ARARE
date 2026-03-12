package com.arare.features.academicterm;

import com.arare.common.enums.AcademicTermStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {

    List<AcademicTerm> findByStatus(AcademicTermStatus status);

    Optional<AcademicTerm> findFirstByStatusOrderByStartDateDesc(AcademicTermStatus status);
}
