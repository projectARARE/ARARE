package com.arare.features.timeslot;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import java.time.LocalTime;

public record TimeslotResponse(
    Long id,
    SchoolDay day,
    LocalTime startTime,
    LocalTime endTime,
    TimeslotType type
) {}
