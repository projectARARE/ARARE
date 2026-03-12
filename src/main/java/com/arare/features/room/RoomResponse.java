package com.arare.features.room;

import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;
import java.util.List;

public record RoomResponse(
    Long id,
    Long buildingId,
    String buildingName,
    String roomNumber,
    RoomType type,
    LabSubtype labSubtype,
    int capacity,
    List<Long> availableTimeslotIds
) {}
