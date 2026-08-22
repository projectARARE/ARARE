package com.arare.features.impact;

import com.arare.common.enums.SchoolDay;
import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.subject.Subject;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyGraphBuilderTest {

    private final DependencyGraphBuilder builder = new DependencyGraphBuilder();

    private Timeslot slot(Long id, SchoolDay day) {
        Timeslot ts = Timeslot.builder()
            .day(day)
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(10, 0))
            .slotNumber(1)
            .type(com.arare.common.enums.TimeslotType.CLASS)
            .build();
        ts.setId(id);
        return ts;
    }

    @Test
    void sectionBasedLabSessionsShareBatchEdgeViaParentBatch() {
        Batch batch = Batch.builder().year(2).section("A").studentCount(60).build();
        batch.setId(1L);
        Subject subject = Subject.builder().name("Physics Lab").isLab(true).build();
        subject.setId(2L);

        ClassSection s1 = ClassSection.builder().label("A1").size(30).build();
        s1.setId(5L);
        s1.setBatch(batch);
        ClassSection s2 = ClassSection.builder().label("A2").size(30).build();
        s2.setId(6L);
        s2.setBatch(batch);

        ClassSession session1 = ClassSession.builder()
            .id(10L).subject(subject).section(s1).timeslot(slot(100L, SchoolDay.MONDAY))
            .duration(1).isLocked(false).build();
        ClassSession session2 = ClassSession.builder()
            .id(11L).subject(subject).section(s2).timeslot(slot(101L, SchoolDay.MONDAY))
            .duration(1).isLocked(false).build();

        DependencyGraph graph = builder.build(List.of(session1, session2));

        assertNotNull(graph.getNode(10L));
        assertEquals(1L, graph.getNode(10L).batchId());
        assertEquals(1L, graph.getNode(11L).batchId());

        boolean hasBatchEdge = graph.getNeighbors(10L).stream()
            .anyMatch(e -> e.targetSessionId().equals(11L) && e.type() == DependencyType.BATCH);
        assertTrue(hasBatchEdge);
    }
}