package com.arare.features.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.subject.Subject;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.universityconfig.UniversityConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

/**
 * P5.4: batchDailyClassesCapFromUniversityConfig joins the active
 * UniversityConfig. With the DB-level single-active guard (V3 migration) and
 * the gateway loading at most one active config, this constraint is driven by
 * exactly one config, so the penalty can never be doubled.
 */
class UniversityConfigConstraintTest {

    private static final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> cv =
        ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class, ClassSession.class);

    private Timeslot slot(Long id, SchoolDay day, int start, int end, int slotNumber) {
        Timeslot ts = Timeslot.builder()
            .day(day).startTime(LocalTime.of(start, 0)).endTime(LocalTime.of(end, 0))
            .slotNumber(slotNumber).type(TimeslotType.CLASS).build();
        ts.setId(id);
        return ts;
    }

    private ClassSession session(Long id, Subject subject, Batch batch, Timeslot ts) {
        ClassSession s = ClassSession.builder()
            .subject(subject).batch(batch).timeslot(ts).duration(1).isLocked(false).build();
        s.setId(id);
        return s;
    }

    @Test
    void batchDailyClassesCapPenalizesExcessAgainstSingleActiveConfig() {
        Subject subject = Subject.builder()
            .name("Maths").requiresTeacher(false).requiresRoom(false)
            .maxSessionsPerDay(10).build();
        subject.setId(1L);
        Batch batch = Batch.builder().studentCount(60).year(2).section("A").build();
        batch.setId(1L);
        Timeslot ts1 = slot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = slot(11L, SchoolDay.MONDAY, 9, 10, 2);
        ClassSession s1 = session(1L, subject, batch, ts1);
        ClassSession s2 = session(2L, subject, batch, ts2);

        UniversityConfig cfg = UniversityConfig.builder()
            .maxClassesPerDay(1).active(true).build();
        cfg.setId(1L);

        cv.verifyThat(TimetableConstraintProvider::batchDailyClassesCapFromUniversityConfig)
            .given(s1, s2, cfg).penalizesBy(1);
    }
}
