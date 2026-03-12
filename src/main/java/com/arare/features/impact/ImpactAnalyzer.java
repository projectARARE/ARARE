package com.arare.features.impact;

import com.arare.features.classsession.ClassSession;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BFS-based impact analyzer.
 *
 * <p>Given a disruption event and the dependency graph of a schedule,
 * produces the <em>minimal</em> set of session IDs that must be rescheduled.</p>
 *
 * <h3>Traversal rules</h3>
 * <ol>
 *   <li>Seed the queue with sessions <em>directly</em> affected by the disruption
 *       (same teacher/room/timeslot on the affected day).</li>
 *   <li>BFS: for each session, follow dependency edges to connected sessions.</li>
 *   <li>Stop expanding from a locked session — it stays in the impacted set
 *       (so the caller can report it) but its own neighbors are not traversed.</li>
 *   <li>Sessions on a different day are ignored for teacher/room disruptions.</li>
 * </ol>
 */
@Component
public class ImpactAnalyzer {

    /**
     * @param event    The disruption to analyze.
     * @param graph    Pre-built dependency graph of the schedule.
     * @param sessions All ClassSessions belonging to the schedule (used for seed selection).
     * @return Ordered set of session IDs that are impacted (BFS order = closest first).
     */
    public Set<Long> analyze(DisruptionRequest event,
                             DependencyGraph graph,
                             List<ClassSession> sessions) {
        Set<Long> impacted = new LinkedHashSet<>();
        Queue<Long> queue = new LinkedList<>(findInitialSessions(event, sessions));

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (impacted.contains(current)) continue;

            impacted.add(current);

            SessionNode node = graph.getNode(current);
            if (node == null) continue;

            // Locked sessions are reported as impacted but traversal stops here;
            // they won't be moved by the solver anyway.
            if (node.locked()) continue;

            for (DependencyEdge edge : graph.getNeighbors(current)) {
                Long neighbor = edge.targetSessionId();
                if (!impacted.contains(neighbor) && shouldExpand(edge)) {
                    queue.add(neighbor);
                }
            }
        }

        return impacted;
    }

    // ------------------------------------------------------------------
    // Seed selection
    // ------------------------------------------------------------------

    private List<Long> findInitialSessions(DisruptionRequest event, List<ClassSession> sessions) {
        return sessions.stream()
                .filter(s -> isDirectlyAffected(s, event))
                .map(ClassSession::getId)
                .collect(Collectors.toList());
    }

    private boolean isDirectlyAffected(ClassSession s, DisruptionRequest event) {
        return switch (event.type()) {
            case TEACHER_UNAVAILABLE ->
                    s.getTeacher() != null
                    && s.getTeacher().getId().equals(event.affectedEntityId())
                    && matchesDay(s, event);

            case ROOM_UNAVAILABLE ->
                    s.getRoom() != null
                    && s.getRoom().getId().equals(event.affectedEntityId())
                    && matchesDay(s, event);

            case TIMESLOT_BLOCKED ->
                    s.getTimeslot() != null
                    && s.getTimeslot().getId().equals(event.affectedEntityId());

            case SESSION_CANCELLED ->
                    s.getId().equals(event.affectedEntityId());

            case SPECIAL_EVENT -> false; // SPECIAL_EVENT seeds are caller-provided
        };
    }

    /**
     * For weekly timetables: the disruption date's day-of-week must match
     * the session's timeslot day (e.g. date is a Monday → only Monday sessions).
     * If no date is provided, all sessions are candidates.
     */
    private boolean matchesDay(ClassSession s, DisruptionRequest event) {
        if (event.date() == null || s.getTimeslot() == null) return true;
        String disruptionDay = event.date().getDayOfWeek().name(); // e.g. "MONDAY"
        return s.getTimeslot().getDay().name().equals(disruptionDay);
    }

    // ------------------------------------------------------------------
    // Expansion rule
    // ------------------------------------------------------------------

    /**
     * Controls how far BFS expands.
     * Currently expands all dependency types — every session sharing a resource
     * with an impacted session is considered potentially conflicted.
     *
     * <p>In future: add granularity (e.g. only expand TEACHER edges, not BATCH edges).</p>
     */
    private boolean shouldExpand(DependencyEdge edge) {
        return true;
    }
}
