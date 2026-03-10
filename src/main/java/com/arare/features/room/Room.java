package com.arare.features.room;

import com.arare.common.BaseEntity;
import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;
import com.arare.features.building.Building;
import com.arare.features.timeslot.Timeslot;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Physical room available for scheduling.
 *
 * <p>Hard constraints enforced by the solver:
 * <ul>
 *   <li>Room cannot host multiple sessions at the same timeslot.</li>
 *   <li>Room capacity must be >= class/section student count.</li>
 *   <li>Room type must match the subject's required room type.</li>
 *   <li>Lab subtype must match when the subject requires a specific lab.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(
    name = "rooms",
    uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "room_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @NotBlank
    @Column(name = "room_number", nullable = false)
    private String roomNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    /**
     * Populated only when type == LAB.
     * Allows matching subject.labSubtypeRequired against room.labSubtype.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private LabSubtype labSubtype;

    @Min(1)
    @Column(nullable = false)
    private int capacity;

    /**
     * Timeslots during which this room is available.
     * If empty, the room is assumed available for all CLASS-type timeslots.
     * Hard constraint: room must not be scheduled in an unavailable timeslot.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "room_availability",
        joinColumns = @JoinColumn(name = "room_id"),
        inverseJoinColumns = @JoinColumn(name = "timeslot_id")
    )
    @Builder.Default
    private List<Timeslot> availableTimeslots = new ArrayList<>();
}
