package com.arare.features.schedule;

import java.util.List;

public interface ScheduleService {
    /** Persist a schedule record and trigger the solver asynchronously. */
    ScheduleResponse generate(ScheduleRequest request);
    ScheduleResponse findById(Long id);
    List<ScheduleResponse> findAll();
    /** Trigger partial re-solve for the given impacted session IDs. */
    ScheduleResponse partialResolve(Long scheduleId, List<Long> impactedSessionIds);
    void delete(Long id);
}
