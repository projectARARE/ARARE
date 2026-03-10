package com.arare.features.batch;

import com.arare.common.BaseEntity;
import com.arare.common.enums.SchoolDay;
import com.arare.features.department.Department;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A student batch (cohort) – equivalent to the "Class" entity in the spec.
 *
 * <p>Named {@code Batch} because {@code class} is a reserved keyword in Java.</p>
 *
 * <p>Examples: CSE-2A, CSE-2B, IT-3A.</p>
 *
 * <p>Hard constraint: no two sessions can be assigned to the same batch
 * at the same timeslot.</p>
 */
@Entity
@Table(
    name = "batches",
    uniqueConstraints = @UniqueConstraint(columnNames = {"department_id", "year", "section"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** Academic year (1–4 for a 4-year program). */
    @Min(1)
    @Column(nullable = false)
    private int year;

    /** Section label, e.g. "A", "B", "C". */
    @Column(nullable = false)
    @Builder.Default
    private String section = "A";

    @Min(1)
    @Column(nullable = false)
    private int studentCount;

    /**
     * Days this batch attends college.
     * Used to filter valid timeslots during scheduling.
     */
    @ElementCollection(targetClass = SchoolDay.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "batch_working_days", joinColumns = @JoinColumn(name = "batch_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day")
    @Builder.Default
    private List<SchoolDay> workingDays = new ArrayList<>();

    /**
     * Preferred free day for this batch.
     * Soft constraint: no sessions should be scheduled on this day.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private SchoolDay preferredFreeDay;
}
