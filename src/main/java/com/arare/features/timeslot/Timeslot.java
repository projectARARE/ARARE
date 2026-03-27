package com.arare.features.timeslot;

import com.arare.common.BaseEntity;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalTime;

// A fixed time period on a given school day.
// <p>The scheduler assigns ClassSessions to CLASS-type timeslots only.
// BREAK and BLOCKED timeslots act as hard-constraint fences.</p>
// <p>Timeslots are global (shared by all departments/rooms). Availability
// per teacher/room is modelled as a ManyToMany relationship in those entities.</p>
@Entity
@Table(
    name = "timeslots",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"day", "start_time", "end_time"}),
        @UniqueConstraint(columnNames = {"day", "slot_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timeslot extends BaseEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SchoolDay day;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_number")
    @Positive
    private Integer slotNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TimeslotType type = TimeslotType.CLASS;

    @PrePersist
    @PreUpdate
    private void validateInvariant() {
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new IllegalStateException("Timeslot startTime must be earlier than endTime.");
        }
    }
}
