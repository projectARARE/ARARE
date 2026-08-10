package com.arare.features.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

/**
 * P5.1: overlap detection must not raise a false hard conflict when a session's
 * timeslot has no slot_number. Before P1 backfilled slot_number, the fallback
 * branch unconditionally returned true for any multi-slot session missing its
 * slot number; the correct behaviour is to compare scheduled start/end times.
 */
class OverlapDetectionTest {

    private static final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> cv =
        ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class, ClassSession.class);

    private Teacher teacher(String name) {
        Teacher t = Teacher.builder().name(name).build();
        t.setId(1L);
        return t;
    }

    private Room room() {
        Room r = Room.builder().roomNumber("R1").build();
        r.setId(1L);
        return r;
    }

    private Timeslot slot(Long id, SchoolDay day, int start, int end, Integer slotNumber) {
        Timeslot ts = Timeslot.builder()
            .day(day)
            .startTime(LocalTime.of(start, 0))
            .endTime(LocalTime.of(end, 0))
            .slotNumber(slotNumber)
            .type(TimeslotType.CLASS)
            .build();
        ts.setId(id);
        return ts;
    }

    private ClassSession session(Long id, Teacher teacher, Room room, Timeslot ts, int duration) {
        ClassSession s = ClassSession.builder()
            .teacher(teacher).room(room).timeslot(ts).duration(duration).isLocked(false)
            .build();
        s.setId(id);
        return s;
    }

    @Test
    void noFalseConflictWhenSlotNumberMissingButTimesDoNotOverlap() {
        Teacher teacher = teacher("Dr. T");
        Timeslot ts1 = slot(10L, SchoolDay.MONDAY, 8, 10, null);   // 08:00-10:00
        Timeslot ts2 = slot(11L, SchoolDay.MONDAY, 10, 12, null);  // 10:00-12:00
        ClassSession s1 = session(1L, teacher, room(), ts1, 2);
        ClassSession s2 = session(2L, teacher, room(), ts2, 2);

        cv.verifyThat(TimetableConstraintProvider::teacherConflict).given(s1, s2).penalizesBy(0);
    }

    @Test
    void conflictDetectedWhenSlotNumberMissingButTimesOverlap() {
        Teacher teacher = teacher("Dr. T");
        Timeslot ts1 = slot(10L, SchoolDay.MONDAY, 8, 10, null);   // 08:00-10:00
        Timeslot ts2 = slot(11L, SchoolDay.MONDAY, 9, 11, null);   // 09:00-11:00
        ClassSession s1 = session(1L, teacher, room(), ts1, 2);
        ClassSession s2 = session(2L, teacher, room(), ts2, 2);

        cv.verifyThat(TimetableConstraintProvider::teacherConflict).given(s1, s2).penalizesBy(1);
    }

    @Test
    void overlapUsesSlotIntervalsWhenSlotNumbersPresent() {
        Teacher teacher = teacher("Dr. T");
        // slots 1-2 (08:00-10:00) and 3-4 (10:00-12:00): adjacent, not overlapping.
        Timeslot ts1 = slot(10L, SchoolDay.MONDAY, 8, 10, 1);
        Timeslot ts2 = slot(11L, SchoolDay.MONDAY, 10, 12, 3);
        ClassSession s1 = session(1L, teacher, room(), ts1, 2);
        ClassSession s2 = session(2L, teacher, room(), ts2, 2);

        cv.verifyThat(TimetableConstraintProvider::teacherConflict).given(s1, s2).penalizesBy(0);
    }
}
