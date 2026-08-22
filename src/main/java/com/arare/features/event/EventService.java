package com.arare.features.event;

import com.arare.features.solvejob.SolveJobResponse;

import java.util.List;

public interface EventService {
    EventResponse create(EventRequest request);
    EventResponse update(Long id, EventRequest request);
    EventResponse findById(Long id);
    List<EventResponse> findAll();
    void delete(Long id);
// Applies the event to the active schedule:
// finds impacted sessions, then triggers asynchronous partial re-optimization.
    SolveJobResponse applyToSchedule(Long eventId, Long scheduleId);
}
