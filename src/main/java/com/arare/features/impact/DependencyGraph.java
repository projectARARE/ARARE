package com.arare.features.impact;

import java.util.*;

/**
 * Adjacency-list directed graph of session dependencies.
 *
 * <p>Each node is a {@link SessionNode} identified by its session ID.
 * Each edge is a {@link DependencyEdge} expressing that two sessions
 * share a resource (teacher, room, or batch) and therefore conflict if
 * both are assigned to the same timeslot.</p>
 *
 * <p>This graph is built in-memory per disruption request and discarded
 * afterwards — it is NOT persisted.</p>
 */
public class DependencyGraph {

    private final Map<Long, SessionNode> nodes = new HashMap<>();
    private final Map<Long, List<DependencyEdge>> adjacency = new HashMap<>();

    public void addNode(SessionNode node) {
        nodes.put(node.sessionId(), node);
        adjacency.putIfAbsent(node.sessionId(), new ArrayList<>());
    }

    /**
     * Adds a directed edge from {@code source} to {@code target}.
     * Call twice (both directions) to create an undirected edge.
     */
    public void addEdge(Long source, Long target, DependencyType type) {
        adjacency.computeIfAbsent(source, k -> new ArrayList<>())
                 .add(new DependencyEdge(source, target, type));
    }

    public SessionNode getNode(Long sessionId) {
        return nodes.get(sessionId);
    }

    public List<DependencyEdge> getNeighbors(Long sessionId) {
        return adjacency.getOrDefault(sessionId, Collections.emptyList());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return adjacency.values().stream().mapToInt(List::size).sum();
    }
}
