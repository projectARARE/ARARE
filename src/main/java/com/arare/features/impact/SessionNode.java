package com.arare.features.impact;

/**
 * Lightweight representation of a ClassSession for use in the dependency graph.
 * Uses only IDs — no Hibernate entity references — to keep the graph pure Java.
 */
public record SessionNode(
    Long sessionId,
    Long teacherId,
    Long roomId,
    Long batchId,
    Long sectionId,
    Long timeslotId,
    /** Day-of-week name, e.g. "MONDAY". Null if session has no timeslot assigned. */
    String day,
    boolean locked
) {}
