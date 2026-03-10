package com.arare.features.classsession;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.room.Room;
import com.arare.features.schedule.Schedule;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import jakarta.persistence.*;
import lombok.*;

/**
 * The core planning entity for the timetable scheduler.
 *
 * <p>Each ClassSession represents one teaching slot that the solver must
 * assign a {@link Teacher}, {@link Room}, and {@link Timeslot} to.</p>
 *
 * <p>Planning variables ({@code teacher}, {@code room}, {@code timeslot}) start
 * as {@code null} and are filled in by the Timefold solver. Their value ranges
 * are provided by the {@link com.arare.features.solver.TimetableSolution}.</p>
 *
 * <p>When {@code isLocked == true} the session already has a
 * {@link com.arare.features.preallocation.PreAllocation} assignment and
 * the solver must not change it. This is enforced via
 * {@code @PlanningPin} or by filtering the value range to a single value.</p>
 *
 * <hr>
 * <b>Session generation logic (handled by SolverService before invoking solver):</b>
 * <pre>
 *   sessionsNeeded = subject.weeklyHours / subject.chunkHours
 *   if (subject.isLab) → one ClassSession per ClassSection
 *   else               → one ClassSession per Batch
 * </pre>
 */
@Entity
@Table(name = "class_sessions")
@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSession {

    /**
     * Using @PlanningId instead of inheriting BaseEntity to avoid
     * Timefold serialisation incompatibilities with @MappedSuperclass auditing.
     */
    @PlanningId
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------------------------------------------
    // Problem facts (fixed inputs; never changed by the solver)
    // ------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /**
     * The batch this session belongs to.
     * Null when this session is for a specific {@link ClassSection} (lab split).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    /**
     * Non-null only for lab sessions where the batch is split into sections.
     * The solver checks {@code section.size} against room capacity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ClassSection section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    /** Duration of this session in hours (copied from {@code subject.chunkHours}). */
    @Column(nullable = false)
    @Builder.Default
    private int duration = 1;

    /**
     * When true, the solver must not reassign this session.
     * Pre-allocated sessions (from {@link com.arare.features.preallocation.PreAllocation})
     * are locked before the solve begins.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean isLocked = false;

    // ------------------------------------------------------------------
    // Planning variables (assigned by Timefold solver)
    // ------------------------------------------------------------------

    /**
     * Assigned teacher.
     * Null for self-study subjects ({@code subject.requiresTeacher == false}).
     */
    @PlanningVariable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    /**
     * Assigned room.
     * Null for off-campus subjects ({@code subject.requiresRoom == false}).
     */
    @PlanningVariable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    /** Assigned timeslot. Always required. */
    @PlanningVariable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeslot_id")
    private Timeslot timeslot;

    // ------------------------------------------------------------------
    // Convenience helpers (not persisted)
    // ------------------------------------------------------------------

    /** Returns the student count relevant for room capacity checking. */
    @Transient
    public int getEffectiveStudentCount() {
        if (section != null) return section.getSize();
        if (batch != null) return batch.getStudentCount();
        return 0;
    }
}
