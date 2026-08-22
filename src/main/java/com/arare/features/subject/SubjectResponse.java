package com.arare.features.subject;

import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;

public record SubjectResponse(
    Long id,
    String name,
    String code,
    Long departmentId,
    String departmentName,
    Long instituteId,
    int weeklyHours,
    int chunkHours,
    RoomType roomTypeRequired,
    LabSubtype labSubtypeRequired,
    boolean isLab,
    boolean requiresTeacher,
    boolean requiresRoom,
    int minGapBetweenSessions,
    int maxSessionsPerDay
) {}
