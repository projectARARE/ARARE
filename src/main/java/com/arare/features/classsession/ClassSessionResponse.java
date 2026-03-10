package com.arare.features.classsession;

/**
 * View DTO for a solved ClassSession, used by the timetable viewer UI.
 *
 * <p>Includes denormalized display names to avoid extra round-trips from the frontend.</p>
 */
public record ClassSessionResponse(
    Long id,
    String subjectName,
    boolean isLab,
    String batchLabel,        // e.g. "CSE-2A"  or section label for lab splits
    Long teacherId,
    String teacherName,
    Long roomId,
    String roomNumber,
    String buildingName,
    Long timeslotId,
    String day,
    String startTime,
    String endTime,
    int duration,
    boolean isLocked
) {}
