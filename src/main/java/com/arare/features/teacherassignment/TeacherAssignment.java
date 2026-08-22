package com.arare.features.teacherassignment;

import com.arare.common.BaseEntity;
import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

// The "allotted" layer of a teacher's term duty: which teacher actually
// teaches which subject, for which batch/section, this academic term.
// <p>This is the two-stage gap from the university domain model:
// <ol>
// <li><b>Qualified</b> (Teacher.subjects) — the subjects a teacher is
// certified to teach, listed on their profile.</li>
// <li><b>Allotted</b> (this entity) — the concrete teaching load assigned
// this term, e.g. "Dr Meena teaches CSE-2A OS this term".</li>
// </ol>
// The solver treats an allotment as a HARD constraint: sessions for the
// (subject, batch/section) class may only be taught by the allotted teacher.
// When no allotment exists the solver falls back to qualified teachers, so
// the feature is fully backward compatible.
// <p>An assignment must reference exactly one of:
// <ul>
// <li>{@code batch} — the whole batch takes the subject as a single class;</li>
// <li>{@code section} — a lab split where each section is taught separately.</li>
// </ul>
// One subject is taught by exactly one teacher per (subject, batch) or
// (subject, section). This mirrors the {@code singleTeacherPerSubjectSection}
// solver constraint, guaranteeing the allotment is always solver-feasible.</p>
@Entity
@Table(name = "teacher_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherAssignment extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    // Allotment scope: exactly one of batch / section must be set.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ClassSection section;

    // Optional per-term override of the teacher's teaching hours for this
    // subject (used for workload reporting; the solver enforces teacher
    // maxDailyHours/maxWeeklyHours from the Teacher profile).
    @Min(1)
    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    // Load priority: higher = more authoritative when a teacher is allotted
    // to overlapping subjects across the institution (1 = default, higher wins).
    @Min(0)
    @Column(nullable = false)
    @Builder.Default
    private int priority = 1;

    @Size(max = 400)
    @Column(length = 400)
    private String notes;

    @PrePersist
    @PreUpdate
    private void validateInvariant() {
        if (batch == null && section == null) {
            throw new IllegalStateException(
                "TeacherAssignment must reference either a batch or a section.");
        }
        if (batch != null && section != null
            && section.getBatch() != null
            && !section.getBatch().getId().equals(batch.getId())) {
            throw new IllegalStateException(
                "TeacherAssignment section " + section.getLabel()
                    + " does not belong to batch " + batch.getSection() + ".");
        }
        if (weeklyHours != null && weeklyHours <= 0) {
            throw new IllegalStateException("TeacherAssignment weeklyHours must be > 0.");
        }
    }
}
