package com.arare.features.academicterm;

import com.arare.common.BaseEntity;
import com.arare.common.enums.AcademicTermStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

/**
 * Represents one academic term (semester / trimester) within a university year.
 *
 * <p>Schedules generated in ARARE can be linked to an academic term
 * to provide temporal versioning — e.g. "CSE Dept – Sem 1 2026".</p>
 */
@Entity
@Table(name = "academic_terms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicTerm extends BaseEntity {

    /** Human-readable name, e.g. "Semester 1 2025–26". */
    @NotBlank
    @Column(nullable = false)
    private String name;

    /** Academic year label, e.g. "2025-26". */
    @Column
    private String academicYear;

    @NotNull
    @Column(nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(nullable = false)
    private LocalDate endDate;

    /** Date when exam period begins (optional — used to block scheduling). */
    @Column
    private LocalDate examPeriodStart;

    /** Date when exam period ends (optional). */
    @Column
    private LocalDate examPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AcademicTermStatus status = AcademicTermStatus.UPCOMING;

    @Column(columnDefinition = "TEXT")
    private String description;
}
