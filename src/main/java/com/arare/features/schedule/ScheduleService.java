package com.arare.features.schedule;

import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.solver.ScoreExplanationResponse;
import com.arare.features.solvejob.SolveJobResponse;

import java.util.List;

public interface ScheduleService {
    SolveJobResponse generate(ScheduleRequest request);
    ScheduleResponse findById(Long id);
    List<ScheduleResponse> findAll();
    ScheduleResponse activate(Long id);
    ScheduleResponse archive(Long id);
    SolveJobResponse partialResolve(Long scheduleId, List<Long> impactedSessionIds);
    ScoreExplanationResponse explainScore(Long scheduleId);
    String getExplanation(Long id);
    void delete(Long id);
    List<ClassSessionResponse> getSessionsBySchedule(Long scheduleId);
    List<ConflictSuggestionResponse> suggestFixes(Long scheduleId, Long sessionId, int limit);
}
