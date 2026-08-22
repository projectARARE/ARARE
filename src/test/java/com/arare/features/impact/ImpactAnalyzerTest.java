package com.arare.features.impact;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P6 regression: teacher/room edges must be same-timeslot scoped so that
 * blocking one day does not flood the teacher's whole week.
 */
class ImpactAnalyzerTest {

    private final DependencyGraphBuilder builder = new DependencyGraphBuilder();
    private final ImpactAnalyzer analyzer = new ImpactAnalyzer();

    private Timeslot slot(Long id, SchoolDay day, LocalTime start) {
        Timeslot ts = Timeslot.builder()
            .day(day).startTime(start).endTime(start.plusHours(1))
            .slotNumber(1).type(TimeslotType.CLASS).build();
        ts.setId(id);
        return ts;
    }

    private ClassSession session(Long id, Teacher teacher, Batch batch, Timeslot ts) {
        return ClassSession.builder()
            .id(id).teacher(teacher).batch(batch).timeslot(ts)
            .duration(1).isLocked(false).build();
    }

    /**
     * Same teacher teaches two sessions: S1 on MONDAY 9-10, S2 on TUESDAY 9-10.
     * Blocking the teacher on Monday must impact only S1's direct Monday
     * conflicts — S2 is on a different day and must NOT be flagged, even
     * though it shares the same teacher (and batch). Before the fix the
     * builder connected S1 and S2 (grouping by teacher regardless of
     * timeslot), so BFS flooded S2.
     */
    @Test
    void teacherDisruptionOnOneDayDoesNotFloodToOtherDays() {
        Teacher teacher = Teacher.builder().name("T").build();
        teacher.setId(1L);
        Batch batch = Batch.builder().year(2).section("A").studentCount(60).build();
        batch.setId(2L);
        Timeslot mon = slot(100L, SchoolDay.MONDAY, LocalTime.of(9, 0));
        Timeslot tue = slot(101L, SchoolDay.TUESDAY, LocalTime.of(9, 0));

        ClassSession s1 = session(1L, teacher, batch, mon);
        ClassSession s2 = session(2L, teacher, batch, tue);

        DependencyGraph graph = builder.build(List.of(s1, s2));

        DisruptionRequest event = new DisruptionRequest(
            DisruptionType.TEACHER_UNAVAILABLE, teacher.getId(),
            LocalDate.of(2024, 1, 1), // Monday per ISO DayOfWeek.MONDAY
            "teacher sick monday");

        Set<Long> impacted = analyzer.analyze(event, graph, List.of(s1, s2));

        assertTrue(impacted.contains(1L), "S1 (Monday, directly hit) must be impacted");
        assertFalse(impacted.contains(2L), "S2 (Tuesday) must NOT be flooded by Monday teacher block");
    }

    /**
     * Two sessions same teacher, same day, same timeslot (different rooms) —
     * these genuinely conflict, so both must be impacted.
     */
    @Test
    void sameTimeslotConflictsAreImpacted() {
        Teacher teacher = Teacher.builder().name("T").build();
        teacher.setId(1L);
        Batch batch = Batch.builder().year(2).section("A").studentCount(60).build();
        batch.setId(2L);
        Timeslot mon = slot(100L, SchoolDay.MONDAY, LocalTime.of(9, 0));

        ClassSession s1 = session(1L, teacher, batch, mon);
        ClassSession s2 = session(2L, teacher, batch, mon);;

        DependencyGraph graph = builder.build(List.of(s1, s2));
        DisruptionRequest event = new DisruptionRequest(
            DisruptionType.TEACHER_UNAVAILABLE, teacher.getId(),
            LocalDate.of(2024, 1, 1), "monday");

        Set<Long> impacted = analyzer.analyze(event, graph, List.of(s1, s2));
        assertTrue(impacted.contains(1L) && impacted.contains(2L),
            "both Monday-same-timeslot sessions must be impacted");
    }

    /**
     * P6 step 4: a dateless SPECIAL_EVENT must NOT match every session.
     * Before the fix, matchesDay() returned true whenever event.date()==null,
     * so a SPECIAL_EVENT with no date flagged the entire schedule. A dateless
     * event has no defined scope and must therefore match nothing.
     */
    @Test
    void datelessSpecialEventDoesNotFlagEverything() {
        Teacher teacher = Teacher.builder().name("T").build();
        teacher.setId(1L);
        Batch batch = Batch.builder().year(2).section("A").studentCount(60).build();
        batch.setId(2L);
        Timeslot mon = slot(100L, SchoolDay.MONDAY, LocalTime.of(9, 0));
        Timeslot tue = slot(101L, SchoolDay.TUESDAY, LocalTime.of(9, 0));

        ClassSession s1 = session(1L, teacher, batch, mon);
        ClassSession s2 = session(2L, teacher, batch, tue);

        DependencyGraph graph = builder.build(List.of(s1, s2));
        DisruptionRequest dateless = new DisruptionRequest(
            DisruptionType.SPECIAL_EVENT, null, null, "unscoped event");

        Set<Long> impacted = analyzer.analyze(dateless, graph, List.of(s1, s2));
        assertTrue(impacted.isEmpty(), "dateless special event must not flood the schedule");
    }

    /**
     * M5 regression: a dateless TEACHER_UNAVAILABLE must report ZERO impact.
     * It has no defined scope (which day is the teacher out?), so the preview
     * would claim sessions were hit while the solver received no fact to move
     * anything — an apply that promised a re-solve and then did nothing.
     */
    @Test
    void datelessTeacherUnavailableReportsNoImpact() {
        Teacher teacher = Teacher.builder().name("T").build();
        teacher.setId(1L);
        Batch batch = Batch.builder().year(2).section("A").studentCount(60).build();
        batch.setId(2L);
        Timeslot mon = slot(100L, SchoolDay.MONDAY, LocalTime.of(9, 0));

        ClassSession s1 = session(1L, teacher, batch, mon);
        ClassSession unassigned = ClassSession.builder()
            .id(3L).teacher(teacher).batch(batch).timeslot(null)
            .duration(1).isLocked(false).build();

        DependencyGraph graph = builder.build(List.of(s1, unassigned));
        DisruptionRequest dateless = new DisruptionRequest(
            DisruptionType.TEACHER_UNAVAILABLE, teacher.getId(), null, "no scope");

        Set<Long> impacted = analyzer.analyze(dateless, graph, List.of(s1, unassigned));
        assertTrue(impacted.isEmpty(), "dateless teacher disruption must not report impact");
    }
}
