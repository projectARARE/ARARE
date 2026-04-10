package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
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

        @Test
        void teacherConsecutiveClassesCapIgnoresNonConsecutiveRuns() {
        Teacher teacher = Teacher.builder().maxConsecutiveClasses(2).maxDailyHours(10).build();
        teacher.setId(1L);

        Timeslot ts1 = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = buildTimeslot(11L, SchoolDay.MONDAY, 10, 11, 3);
        Timeslot ts3 = buildTimeslot(12L, SchoolDay.MONDAY, 12, 13, 5);

        ClassSession s1 = buildSession(1L, teacher, ts1);
        ClassSession s2 = buildSession(2L, teacher, ts2);
        ClassSession s3 = buildSession(3L, teacher, ts3);

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherConsecutiveClassesCap)
            .given(s1, s2, s3)
            .penalizesBy(0);
        }

        @Test
        void roomTypeMismatchPenalizesWhenLabSubtypeDiffers() {
        Subject chemistryLab = Subject.builder()
            .isLab(true)
            .roomTypeRequired(RoomType.LAB)
            .labSubtypeRequired(LabSubtype.CHEMISTRY_LAB)
            .build();

        Room csLabRoom = Room.builder()
            .type(RoomType.LAB)
            .labSubtype(LabSubtype.COMPUTER_LAB)
            .capacity(40)
            .build();

        ClassSession s1 = ClassSession.builder()
            .id(1L)
            .subject(chemistryLab)
            .room(csLabRoom)
            .duration(1)
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::roomTypeMismatch)
            .given(s1)
            .penalizesBy(1);
        }

        @Test
        void mandatoryBatchBreakPenalizesWhenMiddayWindowFullyOccupied() {
        Batch batch = Batch.builder().studentCount(60).year(2).section("A").build();
        batch.setId(1L);

        Timeslot lunch1 = buildTimeslot(20L, SchoolDay.MONDAY, 12, 13, 5);
        Timeslot lunch2 = buildTimeslot(21L, SchoolDay.MONDAY, 13, 14, 6);

        ClassSession s1 = ClassSession.builder()
            .id(1L)
            .batch(batch)
            .timeslot(lunch1)
            .duration(1)
            .build();
        ClassSession s2 = ClassSession.builder()
            .id(2L)
            .batch(batch)
            .timeslot(lunch2)
            .duration(1)
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::mandatoryBatchBreak)
            .given(lunch1, lunch2, s1, s2)
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
