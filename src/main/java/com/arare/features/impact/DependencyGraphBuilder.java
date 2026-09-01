package com.arare.features.impact;

import com.arare.common.enums.SchoolDay;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// Constructs a {@link DependencyGraph} from the sessions belonging to one schedule.
// <p>Three types of edges are created:</p>
// <ul>
// <li><b>TEACHER</b> — sessions sharing the same teacher <em>on the same day
//     </em> (a teacher absent on Monday impacts every Monday session, not the
//     whole week).</li>
// <li><b>ROOM</b>    — sessions sharing the same room <em>on the same day</em>.</li>
// <li><b>BATCH</b>   — sessions for the same batch <em>on the same day</em>
//     (a batch's Monday sections can cascade-reschedule together, but a
//     Monday disruption must not pull in Tuesday's sessions).</li>
// </ul>
// <p>Edges are intentionally <b>not</b> created between sessions on different
// days for teacher/room. The previous version grouped resource edges by resource
// id alone, which connected a teacher's Monday session to their Tuesday session
// and made a Monday-only disruption flood the whole week's schedule during BFS
// contradicting the "minimal set" blast-radius claim in the README and the
// day-matching rules in {@link ImpactAnalyzer}. Day-scoping teacher/room edges
// is the narrower, correct definition, and it is consistent with
// {@link ImpactAnalyzer}'s day-matching rules.</p>
// <p>Sessions with a {@code null} timeslot are not linked by teacher/room edges
// (they have no day to conflict on); the analyzer still seeds them directly when
// appropriate (e.g. a blocked timeslot re-opens all unassigned sessions).
// </p>
// <p>O(n²) in the worst case per resource group, but in practice each group is small
// (a typical teacher has 10–20 sessions), so the graph build is very fast.</p>
@Component
public class DependencyGraphBuilder {

    public DependencyGraph build(List<ClassSession> sessions) {
        DependencyGraph graph = new DependencyGraph();

        // Add nodes first
        for (ClassSession s : sessions) {
            graph.addNode(toNode(s));
        }

        // TEACHER edges: day-scoped so a teacher's Monday absence does not link
        // to their Tuesday sessions.
        sessions.stream()
                .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
                .collect(Collectors.groupingBy(s -> key(s.getTeacher().getId(), s.getTimeslot().getDay())))
                .forEach((k, group) -> connectAll(graph, group, DependencyType.TEACHER));

        // ROOM edges: same-day scoping for the same reason.
        sessions.stream()
                .filter(s -> s.getRoom() != null && s.getTimeslot() != null)
                .collect(Collectors.groupingBy(s -> key(s.getRoom().getId(), s.getTimeslot().getDay())))
                .forEach((k, group) -> connectAll(graph, group, DependencyType.ROOM));

        // BATCH edges: day-scoped — rescheduling a batch's Monday sessions can
        // cascade to its other Monday sessions, but must not pull in Tuesday
        // sessions during a Monday-only disruption (minimal-set rule, same as
        // teacher/room). Uses the section-aware effectiveBatch (a lab session
        // split into sections has batch==null but belongs to its section's
        // parent batch), so section-based sessions are not dropped from the
        // impact graph.
        sessions.stream()
                .filter(s -> s.getEffectiveBatch() != null && s.getTimeslot() != null)
                .collect(Collectors.groupingBy(s -> key(s.getEffectiveBatch().getId(), s.getTimeslot().getDay())))
                .forEach((bid, group) -> connectAll(graph, group, DependencyType.BATCH));

        return graph;
    }

    private static String key(Long resourceId, SchoolDay day) {
        return resourceId + ":" + day;
    }

    // Creates bidirectional edges between every pair of sessions in the group. 
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
        Batch effectiveBatch = s.getEffectiveBatch();
        return new SessionNode(
                s.getId(),
                s.getTeacher()  != null ? s.getTeacher().getId()   : null,
                s.getRoom()     != null ? s.getRoom().getId()      : null,
                effectiveBatch  != null ? effectiveBatch.getId()   : null,
                s.getSection()  != null ? s.getSection().getId()   : null,
                s.getTimeslot() != null ? s.getTimeslot().getId()  : null,
                // Day-of-week name, e.g. "MONDAY". Null if session has no timeslot assigned. 
                s.getTimeslot() != null ? s.getTimeslot().getDay().name() : null,
                s.isLocked()
        );
    }
}
