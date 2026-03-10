package com.arare.features.classsession;

import java.util.List;

public interface ClassSessionService {
    List<ClassSessionResponse> findBySchedule(Long scheduleId);
    List<ClassSessionResponse> findByScheduleAndBatch(Long scheduleId, Long batchId);
    List<ClassSessionResponse> findByScheduleAndTeacher(Long scheduleId, Long teacherId);
}
