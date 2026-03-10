package com.arare.features.timeslot;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** Request DTO: create or update a Timeslot. */
public record TimeslotRequest(
    @NotNull SchoolDay day,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull TimeslotType type
) {}
