package com.arare.features.schedule;

import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.solver.ScoreExplanationResponse;

import java.util.List;

public interface ScheduleService {
    /** Persist a schedule record and trigger the solver asynchronously. */
    ScheduleResponse generate(ScheduleRequest request);
    ScheduleResponse findById(Long id);
    List<ScheduleResponse> findAll();
    /** Trigger partial re-solve for the given impacted session IDs. */
    ScheduleResponse partialResolve(Long scheduleId, List<Long> impactedSessionIds);
    /** Return a constraint-by-constraint score breakdown for the schedule. */
    ScoreExplanationResponse explainScore(Long scheduleId);
    /** Return the stored plain-text score explanation, or a fallback message if unavailable. */
    String getExplanation(Long id);
    void delete(Long id);
    /** Return all ClassSessions belonging to this schedule. */
    List<ClassSessionResponse> getSessionsBySchedule(Long scheduleId);
}
