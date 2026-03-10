package com.arare.features.teacher;

import com.arare.common.BaseEntity;
import com.arare.common.enums.SchoolDay;
import com.arare.features.building.Building;
import com.arare.features.subject.Subject;
import com.arare.features.timeslot.Timeslot;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A faculty member who teaches one or more subjects.
 *
 * <p>Hard constraints:
 * <ul>
 *   <li>Teacher cannot teach two sessions simultaneously.</li>
 *   <li>Teacher must be available at the assigned timeslot.</li>
 *   <li>Teacher must be qualified for the assigned subject ({@code subjects} list).</li>
 * </ul>
 * </p>
 *
 * <p>Medium constraints:
 * <ul>
 *   <li>Daily and weekly hour caps ({@code maxDailyHours}, {@code maxWeeklyHours}).</li>
 *   <li>Consecutive class cap ({@code maxConsecutiveClasses}).</li>
 *   <li>Minimize building changes (uses {@code movementPenalty} weight).</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    /**
     * Subjects this teacher is qualified to teach.
     * Hard constraint: teacher can only be assigned to sessions where
     * the subject is in this list.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "teacher_subjects",
        joinColumns = @JoinColumn(name = "teacher_id"),
        inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    @Builder.Default
    private List<Subject> subjects = new ArrayList<>();

    /**
     * Timeslots during which this teacher is available.
     * If empty, teacher is assumed available for all CLASS-type timeslots.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "teacher_availability",
        joinColumns = @JoinColumn(name = "teacher_id"),
        inverseJoinColumns = @JoinColumn(name = "timeslot_id")
    )
    @Builder.Default
    private List<Timeslot> availableTimeslots = new ArrayList<>();

    /**
     * Buildings the teacher prefers; used for movement minimization.
     * Soft constraint: prefer assigning sessions in these buildings.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "teacher_preferred_buildings",
        joinColumns = @JoinColumn(name = "teacher_id"),
        inverseJoinColumns = @JoinColumn(name = "building_id")
    )
    @Builder.Default
    private List<Building> preferredBuildings = new ArrayList<>();

    /** Maximum teaching hours per day. Medium constraint. */
    @Min(1)
    @Column(nullable = false)
    @Builder.Default
    private int maxDailyHours = 6;

    /** Maximum teaching hours per week. Medium constraint. */
    @Min(1)
    @Column(nullable = false)
    @Builder.Default
    private int maxWeeklyHours = 20;

    /**
     * Maximum number of back-to-back sessions without a gap.
     * Medium constraint.
     */
    @Min(1)
    @Column(nullable = false)
    @Builder.Default
    private int maxConsecutiveClasses = 3;

    /**
     * Penalty weight per building change.
     * Higher values cause the solver to more strongly avoid building switches.
     */
    @Column(nullable = false)
    @Builder.Default
    private int movementPenalty = 1;

    /**
     * Preferred free day (e.g., FRIDAY for a 5-day week).
     * Soft constraint: solver tries to keep this day clear for the teacher.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private SchoolDay preferredFreeDay;
}
