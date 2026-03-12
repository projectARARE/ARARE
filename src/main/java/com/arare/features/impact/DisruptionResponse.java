package com.arare.features.impact;

import java.util.List;

/**
 * Response from the Impact Analyzer.
 * Contains the sessions that will be affected by the disruption
 * before any re-solving takes place.
 */
public record DisruptionResponse(
    DisruptionType type,
    Long affectedEntityId,
    String affectedEntityName,
    String disruption,
    int impactedSessionCount,
    List<ImpactedSession> impactedSessions,
    List<Long> impactedSessionIds
) {
    /** Lightweight session summary used in the disruption preview. */
    public record ImpactedSession(
        Long id,
        String subjectName,
        String batchLabel,
        String teacherName,
        String roomNumber,
        String day,
        String startTime,
        String endTime,
        boolean locked
    ) {}
}
