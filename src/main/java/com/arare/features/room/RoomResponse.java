package com.arare.features.room;

import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;

public record RoomResponse(
    Long id,
    Long buildingId,
    String buildingName,
    String roomNumber,
    RoomType type,
    LabSubtype labSubtype,
    int capacity
) {}
