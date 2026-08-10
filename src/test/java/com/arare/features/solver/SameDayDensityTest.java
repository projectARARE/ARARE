package com.arare.features.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.subject.Subject;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P5.3: same-day subject density must be owned by a single rule
 * (avoidSameSubjectMultipleTimesPerDay, gated by Subject.maxSessionsPerDay).
 * The old spreadSubjectAcrossWeek duplicated the same-day penalty, so two
 * same-day sessions within the cap were penalized twice.
 */
class SameDayDensityTest {

    private static final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> cv =
        ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class, ClassSession.class);

    private Timeslot slot(Long id, SchoolDay day, int start, int end, int slotNumber) {
        Timeslot ts = Timeslot.builder()
            .day(day).startTime(LocalTime.of(start, 0)).endTime(LocalTime.of(end, 0))
            .slotNumber(slotNumber).type(TimeslotType.CLASS).build();
        ts.setId(id);
        return ts;
    }

    private Subject subject(String name, int maxPerDay) {
        Subject s = Subject.builder()
            .name(name).requiresTeacher(false).requiresRoom(false).maxSessionsPerDay(maxPerDay)
            .build();
        s.setId(1L);
        return s;
    }

    private Batch batch() {
        Batch b = Batch.builder().studentCount(60).year(2).section("A").build();
        b.setId(1L);
        return b;
    }

    private ClassSession session(Long id, Subject subject, Batch batch, Timeslot ts) {
        ClassSession s = ClassSession.builder()
            .subject(subject).batch(batch).timeslot(ts).duration(1).isLocked(false).build();
        s.setId(id);
        return s;
    }

    @Test
    void sameDayDensityWithinCapNotPenalized() {
        Subject subject = subject("Maths", 2);
        Batch batch = batch();
        Timeslot ts1 = slot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = slot(11L, SchoolDay.MONDAY, 9, 10, 2);
        ClassSession s1 = session(1L, subject, batch, ts1);
        ClassSession s2 = session(2L, subject, batch, ts2);

        cv.verifyThat(TimetableConstraintProvider::avoidSameSubjectMultipleTimesPerDay)
            .given(s1, s2).penalizesBy(0);
    }

    @Test
    void sameDayDensityBeyondCapPenalizesExcess() {
        Subject subject = subject("Maths", 1);
        Batch batch = batch();
        Timeslot ts1 = slot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = slot(11L, SchoolDay.MONDAY, 9, 10, 2);
        ClassSession s1 = session(1L, subject, batch, ts1);
        ClassSession s2 = session(2L, subject, batch, ts2);

        // count 2 - max 1 = 1
        cv.verifyThat(TimetableConstraintProvider::avoidSameSubjectMultipleTimesPerDay)
            .given(s1, s2).penalizesBy(1);
    }

    @Test
    void fullProviderDoesNotDoublePenalizeSameDayWithinCap() {
        Subject subject = subject("Maths", 2);
        Batch batch = batch();
        Timeslot ts1 = slot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = slot(11L, SchoolDay.MONDAY, 9, 10, 2);
        ClassSession s1 = session(1L, subject, batch, ts1);
        ClassSession s2 = session(2L, subject, batch, ts2);

        TimetableSolution solution = new TimetableSolution(
            List.of(ts1, ts2), List.of(), List.of(), List.of(subject),
            List.of(batch), List.of(), List.of(), List.of(),
            List.of(s1, s2), null);

        SolverConfig config = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(ClassSession.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(
                new TerminationConfig().withSpentLimit(Duration.ZERO));
        TimetableSolution solved = SolverFactory.<TimetableSolution>create(config)
            .buildSolver().solve(solution);

        // No hard/soft/medium penalty: two same-day sessions within the cap
        // are acceptable, with no duplicate penalty from the removed spread rule.
        assertEquals(0, solved.getScore().mediumScore());
    }
}
