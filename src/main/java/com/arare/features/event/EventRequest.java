package com.arare.features.event;

import com.arare.common.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Request DTO: register a disruption event. */
public record EventRequest(
    @NotBlank String title,
    @NotNull EventType type,
    LocalDate startDate,
    LocalDate endDate,
    String description,
    List<Long> affectedRoomIds,
    List<Long> affectedTeacherIds,
    List<Long> affectedTimeslotIds
) {}
