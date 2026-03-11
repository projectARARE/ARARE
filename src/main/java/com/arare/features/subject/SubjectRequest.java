package com.arare.features.subject;

import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request DTO: create or update a Subject. */
public record SubjectRequest(
    @NotBlank String name,
    String code,
    @NotNull Long departmentId,
    @Min(1) int weeklyHours,
    @Min(1) int chunkHours,
    @NotNull RoomType roomTypeRequired,
    LabSubtype labSubtypeRequired,
    boolean isLab,
    boolean requiresTeacher,
    boolean requiresRoom,
    @Min(0) int minGapBetweenSessions,
    @Min(1) int maxSessionsPerDay
) {}
