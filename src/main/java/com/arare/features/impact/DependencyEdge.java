package com.arare.features.impact;

/**
 * A directed edge in the session dependency graph.
 *
 * <p>Edges are bidirectional at the logical level (if A depends on B then B is
 * affected whenever A is rescheduled), but we store them as directed edges pointing
 * source → target so that BFS can follow them in one direction per traversal step.</p>
 */
public record DependencyEdge(
    Long sourceSessionId,
    Long targetSessionId,
    DependencyType type
) {}
