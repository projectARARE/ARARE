package com.arare.features.event;

import java.util.List;

public interface EventService {
    EventResponse create(EventRequest request);
    EventResponse findById(Long id);
    List<EventResponse> findAll();
    void delete(Long id);
    /**
     * Applies the event to the active schedule:
     * marks affected slots as BLOCKED, then triggers partial re-optimization.
     */
    void applyToSchedule(Long eventId, Long scheduleId);
}
