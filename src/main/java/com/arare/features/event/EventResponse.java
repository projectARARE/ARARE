package com.arare.features.event;

import com.arare.common.enums.EventType;
import java.time.LocalDate;
import java.util.List;

public record EventResponse(
    Long id,
    String title,
    EventType type,
    LocalDate startDate,
    LocalDate endDate,
    String description,
    List<Long> affectedRoomIds,
    List<Long> affectedTeacherIds,
    List<Long> affectedTimeslotIds
) {}
