package com.arare.features.preallocation;

public record PreAllocationResponse(
    Long id,
    Long scheduleId,
    Long batchId,
    String batchLabel,
    Long subjectId,
    String subjectName,
    Long teacherId,
    String teacherName,
    Long roomId,
    String roomNumber,
    Long timeslotId,
    String day,
    String startTime,
    boolean locked
) {}
