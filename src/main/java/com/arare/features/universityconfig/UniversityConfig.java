package com.arare.features.universityconfig;

import com.arare.common.BaseEntity;
import com.arare.common.enums.SchoolDay;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Global scheduling configuration for the university.
 *
 * <p>Only one active config record should exist at a time.
 * Changing this record and re-running the solver generates a new schedule version.</p>
 *
 * <p>Key knobs:
 * <ul>
 *   <li>{@code daysPerWeek} – 5 (Mon–Fri) or 6 (Mon–Sat)</li>
 *   <li>{@code timeslotsPerDay} – total period slots per day</li>
 *   <li>{@code maxClassesPerDay} – student cognitive load cap per day</li>
 *   <li>{@code breakSlotIndices} – ordered indices of break periods</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "university_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityConfig extends BaseEntity {

    @Column(nullable = false, unique = true)
    @Builder.Default
    private boolean active = true;

    /** 5 for Mon–Fri, 6 for Mon–Sat. */
    @Min(5)
    @Max(6)
    @Column(nullable = false)
    @Builder.Default
    private int daysPerWeek = 5;

    /** Number of scheduling periods per day, including breaks. */
    @Min(1)
    @Column(nullable = false)
    @Builder.Default
    private int timeslotsPerDay = 8;

    /**
     * Maximum class sessions a student batch can have in a single day.
     * Medium constraint enforced by the ConstraintProvider.
     */
    @Min(1)
    @Column(nullable = false)
    @Builder.Default
    private int maxClassesPerDay = 6;

    /**
     * 1-based indices of break periods within a day.
     * Example: [4] means the 4th period of every day is a lunch break.
     * The timeslot generator marks these as {@code TimeslotType.BREAK}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "config_break_indices", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "slot_index")
    @Builder.Default
    private List<Integer> breakSlotIndices = new ArrayList<>();

    /**
     * Working days configured for this university.
     * Derived from daysPerWeek but stored explicitly so display code
     * doesn't need to recompute the set.
     */
    @ElementCollection(targetClass = SchoolDay.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "config_working_days", joinColumns = @JoinColumn(name = "config_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day")
    @Builder.Default
    private List<SchoolDay> workingDays = new ArrayList<>();
}
