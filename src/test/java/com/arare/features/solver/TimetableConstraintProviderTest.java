package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.classsession.ClassSession;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

class TimetableConstraintProviderTest {

    ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> constraintVerifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class, ClassSession.class);

    // ── Teacher daily hours cap should be MEDIUM, not HARD ──

    @Test
    void teacherDailyHoursCapPenalizesMedium() {
        Teacher teacher = Teacher.builder().maxDailyHours(2).build();
        teacher.setId(1L);

        Timeslot ts1 = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = buildTimeslot(11L, SchoolDay.MONDAY, 9, 10, 2);
        Timeslot ts3 = buildTimeslot(12L, SchoolDay.MONDAY, 10, 11, 3);

        ClassSession s1 = buildSession(1L, teacher, ts1);
        ClassSession s2 = buildSession(2L, teacher, ts2);
        ClassSession s3 = buildSession(3L, teacher, ts3);

        // Total 3 hours, max is 2 → penalty = 1
        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherDailyHoursCap)
                .given(s1, s2, s3)
                .penalizesBy(1);
    }

    // ── Teacher weekly hours cap should be MEDIUM, not HARD ──

    @Test
    void teacherWeeklyHoursCapPenalizesMedium() {
        Teacher teacher = Teacher.builder().maxWeeklyHours(2).maxDailyHours(10).build();
        teacher.setId(1L);

        Timeslot ts1 = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = buildTimeslot(11L, SchoolDay.TUESDAY, 8, 9, 1);
        Timeslot ts3 = buildTimeslot(12L, SchoolDay.WEDNESDAY, 8, 9, 1);

        ClassSession s1 = buildSession(1L, teacher, ts1);
        ClassSession s2 = buildSession(2L, teacher, ts2);
        ClassSession s3 = buildSession(3L, teacher, ts3);

        // Total 3 hours across week, max is 2 → penalty = 1
        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherWeeklyHoursCap)
                .given(s1, s2, s3)
                .penalizesBy(1);
    }

    // ── Teacher consecutive classes cap should be MEDIUM, not HARD ──

    @Test
    void teacherConsecutiveClassesCapPenalizesMedium() {
        Teacher teacher = Teacher.builder().maxConsecutiveClasses(2).maxDailyHours(10).build();
        teacher.setId(1L);

        Timeslot ts1 = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = buildTimeslot(11L, SchoolDay.MONDAY, 9, 10, 2);
        Timeslot ts3 = buildTimeslot(12L, SchoolDay.MONDAY, 10, 11, 3);

        ClassSession s1 = buildSession(1L, teacher, ts1);
        ClassSession s2 = buildSession(2L, teacher, ts2);
        ClassSession s3 = buildSession(3L, teacher, ts3);

        // 3 classes on same day, max consecutive is 2 → penalty = 1
        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherConsecutiveClassesCap)
                .given(s1, s2, s3)
                .penalizesBy(1);
    }

    // ── Helpers ──

    private Timeslot buildTimeslot(Long id, SchoolDay day, int startHour, int endHour, int slotNumber) {
        Timeslot ts = Timeslot.builder()
                .day(day)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(endHour, 0))
                .slotNumber(slotNumber)
                .type(TimeslotType.CLASS)
                .build();
        ts.setId(id);
        return ts;
    }

    private ClassSession buildSession(Long id, Teacher teacher, Timeslot timeslot) {
        ClassSession s = ClassSession.builder()
                .teacher(teacher)
                .timeslot(timeslot)
                .duration(1)
                .build();
        s.setId(id);
        return s;
    }
}
