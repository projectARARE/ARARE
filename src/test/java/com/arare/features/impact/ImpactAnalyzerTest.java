package com.arare.features.impact;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.classsession.ClassSession;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactAnalyzerTest {

    @Test
    void timeslotBlockedIncludesUnassignedSessions() {
        Timeslot blocked = Timeslot.builder()
            .day(SchoolDay.MONDAY)
            .startTime(LocalTime.of(8, 0))
            .endTime(LocalTime.of(9, 0))
            .type(TimeslotType.CLASS)
            .build();
        blocked.setId(100L);

        ClassSession assigned = ClassSession.builder().timeslot(blocked).duration(1).isLocked(false).build();
        assigned.setId(1L);
        ClassSession unassigned = ClassSession.builder().timeslot(null).duration(1).isLocked(false).build();
        unassigned.setId(2L);

        DependencyGraph graph = new DependencyGraph();
        graph.addNode(new SessionNode(1L, null, null, null, null, 100L, "MONDAY", false));
        graph.addNode(new SessionNode(2L, null, null, null, null, null, null, false));

        ImpactAnalyzer analyzer = new ImpactAnalyzer();
        Set<Long> impacted = analyzer.analyze(
            new DisruptionRequest(DisruptionType.TIMESLOT_BLOCKED, 100L, null, null),
            graph,
            List.of(assigned, unassigned)
        );

        assertTrue(impacted.contains(1L));
        assertTrue(impacted.contains(2L));
    }

    @Test
    void specialEventUsesDayMatching() {
        Timeslot monday = Timeslot.builder()
            .day(SchoolDay.MONDAY)
            .startTime(LocalTime.of(10, 0))
            .endTime(LocalTime.of(11, 0))
            .type(TimeslotType.CLASS)
            .build();
        monday.setId(10L);

        Timeslot tuesday = Timeslot.builder()
            .day(SchoolDay.TUESDAY)
            .startTime(LocalTime.of(10, 0))
            .endTime(LocalTime.of(11, 0))
            .type(TimeslotType.CLASS)
            .build();
        tuesday.setId(11L);

        ClassSession sMon = ClassSession.builder().timeslot(monday).duration(1).isLocked(false).build();
        sMon.setId(10L);
        ClassSession sTue = ClassSession.builder().timeslot(tuesday).duration(1).isLocked(false).build();
        sTue.setId(11L);

        DependencyGraph graph = new DependencyGraph();
        graph.addNode(new SessionNode(10L, null, null, null, null, 10L, "MONDAY", false));
        graph.addNode(new SessionNode(11L, null, null, null, null, 11L, "TUESDAY", false));

        ImpactAnalyzer analyzer = new ImpactAnalyzer();
        Set<Long> impacted = analyzer.analyze(
            new DisruptionRequest(DisruptionType.SPECIAL_EVENT, null, LocalDate.parse("2026-03-30"), "event"),
            graph,
            List.of(sMon, sTue)
        );

        assertTrue(impacted.contains(10L));
        assertTrue(!impacted.contains(11L));
    }
}
