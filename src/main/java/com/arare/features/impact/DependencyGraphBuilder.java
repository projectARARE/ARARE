package com.arare.features.impact;

import com.arare.features.classsession.ClassSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Constructs a {@link DependencyGraph} from the sessions belonging to one schedule.
 *
 * <p>Three types of edges are created:</p>
 * <ul>
 *   <li><b>TEACHER</b> — sessions sharing the same teacher conflict on the same timeslot.</li>
 *   <li><b>ROOM</b>    — sessions sharing the same room conflict on the same timeslot.</li>
 *   <li><b>BATCH</b>   — sessions for the same batch conflict with each other.</li>
 * </ul>
 *
 * <p>O(n²) in the worst case per resource group, but in practice each group is small
 * (a typical teacher has 10–20 sessions), so the graph build is very fast.</p>
 */
@Component
public class DependencyGraphBuilder {

    public DependencyGraph build(List<ClassSession> sessions) {
        DependencyGraph graph = new DependencyGraph();

        // Add nodes first
        for (ClassSession s : sessions) {
            graph.addNode(toNode(s));
        }

        // Build teacher conflict edges
        sessions.stream()
                .filter(s -> s.getTeacher() != null)
                .collect(Collectors.groupingBy(s -> s.getTeacher().getId()))
                .forEach((tid, group) -> connectAll(graph, group, DependencyType.TEACHER));

        // Build room conflict edges
        sessions.stream()
                .filter(s -> s.getRoom() != null)
                .collect(Collectors.groupingBy(s -> s.getRoom().getId()))
                .forEach((rid, group) -> connectAll(graph, group, DependencyType.ROOM));

        // Build batch conflict edges
        sessions.stream()
                .filter(s -> s.getBatch() != null)
                .collect(Collectors.groupingBy(s -> s.getBatch().getId()))
                .forEach((bid, group) -> connectAll(graph, group, DependencyType.BATCH));

        return graph;
    }

    /** Creates bidirectional edges between every pair of sessions in the group. */
    private void connectAll(DependencyGraph graph, List<ClassSession> group, DependencyType type) {
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                Long a = group.get(i).getId();
                Long b = group.get(j).getId();
                graph.addEdge(a, b, type);
                graph.addEdge(b, a, type);
            }
        }
    }

    private SessionNode toNode(ClassSession s) {
        return new SessionNode(
                s.getId(),
                s.getTeacher()  != null ? s.getTeacher().getId()   : null,
                s.getRoom()     != null ? s.getRoom().getId()       : null,
                s.getBatch()    != null ? s.getBatch().getId()      : null,
                s.getSection()  != null ? s.getSection().getId()    : null,
                s.getTimeslot() != null ? s.getTimeslot().getId()   : null,
                s.getTimeslot() != null ? s.getTimeslot().getDay().name() : null,
                s.isLocked()
        );
    }
}
