package com.arare.features.schedule;

import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.solver.ScoreExplanationResponse;

import java.util.List;

public interface ScheduleService {
    ScheduleResponse generate(ScheduleRequest request);
    ScheduleResponse findById(Long id);
    List<ScheduleResponse> findAll();
    ScheduleResponse partialResolve(Long scheduleId, List<Long> impactedSessionIds);
    ScoreExplanationResponse explainScore(Long scheduleId);
    String getExplanation(Long id);
    void delete(Long id);
    List<ClassSessionResponse> getSessionsBySchedule(Long scheduleId);
    List<ConflictSuggestionResponse> suggestFixes(Long scheduleId, Long sessionId, int limit);
}
