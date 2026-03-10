package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.ScheduleStatus;

public record ScheduleResponse(
    Long id,
    String name,
    ScheduleScope scope,
    ScheduleStatus status,
    Long parentScheduleId,
    String score,
    String scoreExplanation,
    String createdAt
) {}
