package com.arare.features.event;

import com.arare.common.BaseEntity;
import com.arare.common.enums.EventType;
import com.arare.features.room.Room;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A real-world disruption that triggers partial re-optimization.
 *
 * <p>When an event is recorded the SolverService:
 * <ol>
 *   <li>Marks affected timeslots as {@code BLOCKED}.</li>
 *   <li>Marks affected teachers as unavailable for those timeslots.</li>
 *   <li>Marks affected rooms as unavailable for those timeslots.</li>
 *   <li>Identifies the impacted {@link com.arare.features.classsession.ClassSession}s.</li>
 *   <li>Runs a partial re-solve for only those sessions.</li>
 * </ol>
 * </p>
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Rooms that are unavailable due to this event. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "event_affected_rooms",
        joinColumns = @JoinColumn(name = "event_id"),
        inverseJoinColumns = @JoinColumn(name = "room_id")
    )
    @Builder.Default
    private List<Room> affectedRooms = new ArrayList<>();

    /** Teachers unavailable due to this event (e.g. leave, seminar participation). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "event_affected_teachers",
        joinColumns = @JoinColumn(name = "event_id"),
        inverseJoinColumns = @JoinColumn(name = "teacher_id")
    )
    @Builder.Default
    private List<Teacher> affectedTeachers = new ArrayList<>();

    /** Timeslots blocked by this event (e.g. exam blocks specific periods). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "event_affected_timeslots",
        joinColumns = @JoinColumn(name = "event_id"),
        inverseJoinColumns = @JoinColumn(name = "timeslot_id")
    )
    @Builder.Default
    private List<Timeslot> affectedTimeslots = new ArrayList<>();
}
