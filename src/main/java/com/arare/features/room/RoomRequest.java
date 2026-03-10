package com.arare.features.room;

import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Request DTO: create or update a Room. */
public record RoomRequest(
    @NotNull Long buildingId,
    @NotNull String roomNumber,
    @NotNull RoomType type,
    LabSubtype labSubtype,
    @Min(1) int capacity,
    /** IDs of Timeslots during which this room is available. Empty = always available. */
    List<Long> availableTimeslotIds
) {}
