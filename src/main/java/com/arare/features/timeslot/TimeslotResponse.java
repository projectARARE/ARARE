package com.arare.features.timeslot;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

public record TimeslotResponse(
    Long id,
    Long version,
    SchoolDay day,
    @JsonFormat(pattern = "HH:mm")
    LocalTime startTime,
    @JsonFormat(pattern = "HH:mm")
    LocalTime endTime,
    Integer slotNumber,
    TimeslotType type
) {}
