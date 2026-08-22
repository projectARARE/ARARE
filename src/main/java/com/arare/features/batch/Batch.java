package com.arare.features.batch;

import com.arare.common.BaseEntity;
import com.arare.common.enums.SchoolDay;
import com.arare.features.department.Department;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// <p>Examples: CSE-2A, CSE-2B, IT-3A.</p>
// <p>Hard constraint: no two sessions can be assigned to the same batch
// at the same timeslot.</p>

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

    // Academic year (1ΓÇô4 for a 4-year program). 
    @Min(1)
    @Column(nullable = false)
    private int year;

    // Section label, e.g. "A", "B", "C". 
    @NotBlank
    @Size(max = 10)
    @Column(nullable = false)
    @Builder.Default
    private String section = "A";

    @Min(1)
    @Column(nullable = false)
    private int studentCount;

// Days this batch attends college.
// Used to filter valid timeslots during scheduling.
    @ElementCollection(targetClass = SchoolDay.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "batch_working_days", joinColumns = @JoinColumn(name = "batch_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day")
    @Builder.Default
    private List<SchoolDay> workingDays = new ArrayList<>();

// Preferred free day for this batch.
// Soft constraint: no sessions should be scheduled on this day.
    @Enumerated(EnumType.STRING)
    @Column
    private SchoolDay preferredFreeDay;

    // Home lecture classroom this batch MUST use for non-lab lectures
    // (homeRoomViolation HARD constraint). Lab sessions and subjects that
    // require a LAB-type room are exempt -- they legitimately need
    // specialised rooms.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_room_id")
    private Room homeRoom;

    // Curriculum for this batch: the subjects it actually offers this term
    // (choice-based specialisation). Empty = inherit every subject the
    // department offers (backward compatible). The session generator scopes
    // to this list and the solver uses it for teacher-allotment lookup.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "batch_subjects",
        joinColumns = @JoinColumn(name = "batch_id"),
        inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<Subject> subjects = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (section != null) {
            section = section.trim().toUpperCase();
        }
        if (workingDays != null && !workingDays.isEmpty()) {
            List<SchoolDay> deduped = new ArrayList<>(new LinkedHashSet<>(workingDays));
            workingDays.clear();
            workingDays.addAll(deduped);
        }
    }
}
