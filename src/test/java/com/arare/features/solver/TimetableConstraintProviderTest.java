package com.arare.features.solver;

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
import java.util.List;

class TimetableConstraintProviderTest {

    ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> constraintVerifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class, ClassSession.class);

    // -- Teacher daily hours cap should be MEDIUM, not HARD --

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

        // Total 3 hours, max is 2 -- penalty = 1
        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherDailyHoursCap)
                .given(s1, s2, s3)
                .penalizesBy(1);
    }

    // -- Teacher weekly hours cap should be MEDIUM, not HARD --

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

        // Total 3 hours across week, max is 2 -- penalty = 1
        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherWeeklyHoursCap)
                .given(s1, s2, s3)
                .penalizesBy(1);
    }

    // -- Teacher consecutive classes cap should be MEDIUM, not HARD --

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

        // 3 classes on same day, max consecutive is 2 -- penalty = 1
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

    // -- Malformed data: sessions with a null subject must never NPE --

    @Test
    void nullSubjectSessionsDoNotBreakTeacherRequiredConstraint() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);

        ClassSession s1 = ClassSession.builder()
            .id(1L)
            .teacher(teacher)
            .duration(1)
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherRequiredButMissing)
            .given(s1)
            .penalizesBy(0);
    }

    @Test
    void nullSubjectSessionsDoNotBreakTeacherNotQualifiedConstraint() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);

        ClassSession s1 = ClassSession.builder()
            .id(1L)
            .teacher(teacher)
            .duration(1)
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherNotQualified)
            .given(s1)
            .penalizesBy(0);
    }

    @Test
    void nullSubjectSessionsDoNotBreakTeacherNotRequiredConstraint() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);

        ClassSession s1 = ClassSession.builder()
            .id(1L)
            .teacher(teacher)
            .duration(1)
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherAssignedWhenNotRequired)
            .given(s1)
            .penalizesBy(0);
    }

    @Test
    void nullSubjectSessionsDoNotBreakRoomRequiredConstraint() {
        Room room = Room.builder().type(RoomType.LECTURE).capacity(40).build();
        room.setId(1L);

        ClassSession s1 = ClassSession.builder()
            .id(1L)
            .room(room)
            .duration(1)
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::roomRequiredButMissing)
            .given(s1)
            .penalizesBy(0);
    }

    @Test
    void nullSubjectSessionsDoNotBreakSameDayDensityConstraint() {
        Timeslot ts1 = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = buildTimeslot(11L, SchoolDay.MONDAY, 9, 10, 2);

        ClassSession s1 = ClassSession.builder().id(1L).timeslot(ts1).duration(1).build();
        ClassSession s2 = ClassSession.builder().id(2L).timeslot(ts2).duration(1).build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::avoidSameSubjectMultipleTimesPerDay)
            .given(s1, s2)
            .penalizesBy(0);
    }

    @Test
    void subjectWithoutMaxSessionsPerDayDoesNotBreakSameDayDensityConstraint() {
        Subject subject = Subject.builder().name("Unbounded Subject").build();
        subject.setId(1L);

        Timeslot ts1 = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot ts2 = buildTimeslot(11L, SchoolDay.MONDAY, 9, 10, 2);

        ClassSession s1 = ClassSession.builder().id(1L).subject(subject).timeslot(ts1).duration(1).build();
        ClassSession s2 = ClassSession.builder().id(2L).subject(subject).timeslot(ts2).duration(1).build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::avoidSameSubjectMultipleTimesPerDay)
            .given(s1, s2)
            .penalizesBy(0);
    }

    // -- Teacher term allotment: only the allotted teacher may be assigned --

    @Test
    void teacherNotAssignedToClassPenalizesNonAllottedTeacher() {
        Teacher allotted = Teacher.builder().build();
        allotted.setId(1L);
        Teacher other = Teacher.builder().build();
        other.setId(2L);

        Subject subject = Subject.builder().name("DSA").requiresTeacher(true).build();
        subject.setId(10L);

        ClassSession s = ClassSession.builder()
            .id(1L)
            .subject(subject)
            .teacher(other)
            .duration(1)
            .allowedTeacherIds(List.of(1L))
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherNotAssignedToClass)
            .given(s)
            .penalizesBy(1);
    }

    @Test
    void teacherNotAssignedToClassAllowsAllottedTeacher() {
        Teacher allotted = Teacher.builder().build();
        allotted.setId(1L);

        Subject subject = Subject.builder().name("DSA").requiresTeacher(true).build();
        subject.setId(10L);

        ClassSession s = ClassSession.builder()
            .id(1L)
            .subject(subject)
            .teacher(allotted)
            .duration(1)
            .allowedTeacherIds(List.of(1L))
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherNotAssignedToClass)
            .given(s)
            .penalizesBy(0);
    }

    @Test
    void teacherNotAssignedToClassFallsBackWhenNoAllotment() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);

        Subject subject = Subject.builder().name("DSA").requiresTeacher(true).build();
        subject.setId(10L);

        ClassSession s = ClassSession.builder()
            .id(1L)
            .subject(subject)
            .teacher(teacher)
            .duration(1)
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherNotAssignedToClass)
            .given(s)
            .penalizesBy(0);
    }

    @Test
    void teacherNotAssignedToClassIgnoresLockedSessions() {
        Teacher allotted = Teacher.builder().build();
        allotted.setId(1L);
        Teacher other = Teacher.builder().build();
        other.setId(2L);

        Subject subject = Subject.builder().name("DSA").requiresTeacher(true).build();
        subject.setId(10L);

        ClassSession s = ClassSession.builder()
            .id(1L)
            .subject(subject)
            .teacher(other)
            .duration(1)
            .isLocked(true)
            .allowedTeacherIds(List.of(1L))
            .build();

        constraintVerifier.verifyThat(TimetableConstraintProvider::teacherNotAssignedToClass)
            .given(s)
            .penalizesBy(0);
    }

    // -- Churn priority: minimizeMovedSessions uses PreviousAssignment facts --

    private ClassSession buildMovableSession(Long id, Subject subject, Batch batch, Teacher teacher,
                                              Room room, Timeslot timeslot) {
        return ClassSession.builder()
            .id(id)
            .subject(subject)
            .batch(batch)
            .teacher(teacher)
            .room(room)
            .timeslot(timeslot)
            .duration(1)
            .build();
    }

    @Test
    void minimizeMovedSessionsPenalizesSessionThatMovedFromBaseline() {
        Subject subject = Subject.builder().name("DSA").build();
        subject.setId(10L);
        Batch batch = Batch.builder().year(2).section("A").build();
        batch.setId(1L);

        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);
        Room room = Room.builder().type(RoomType.LECTURE).capacity(60).build();
        room.setId(1L);
        Timeslot oldSlot = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot newSlot = buildTimeslot(11L, SchoolDay.MONDAY, 9, 10, 2);

        // The session has moved to a DIFFERENT timeslot than its baseline.
        ClassSession moved = buildMovableSession(1L, subject, batch, teacher, room, newSlot);
        PreviousAssignment baseline = new PreviousAssignment(
            PreviousAssignment.keyFor(moved), teacher.getId(), room.getId(), oldSlot.getId());

        constraintVerifier.verifyThat(TimetableConstraintProvider::minimizeMovedSessions)
            .given(moved, baseline)
            .penalizesBy(1);
    }

    @Test
    void minimizeMovedSessionsDoesNotPenalizeSessionInBaselinePosition() {
        Subject subject = Subject.builder().name("DSA").build();
        subject.setId(10L);
        Batch batch = Batch.builder().year(2).section("A").build();
        batch.setId(1L);

        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);
        Room room = Room.builder().type(RoomType.LECTURE).capacity(60).build();
        room.setId(1L);
        Timeslot slot = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);

        ClassSession stable = buildMovableSession(1L, subject, batch, teacher, room, slot);
        PreviousAssignment baseline = new PreviousAssignment(
            PreviousAssignment.keyFor(stable), teacher.getId(), room.getId(), slot.getId());

        constraintVerifier.verifyThat(TimetableConstraintProvider::minimizeMovedSessions)
            .given(stable, baseline)
            .penalizesBy(0);
    }

    // -- Helpers --

    // -- Disruption facts --

    @Test
    void disruptionTeacherUnavailablePenalizesSessionsOnThatDay() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);
        Timeslot monday = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot tuesday = buildTimeslot(11L, SchoolDay.TUESDAY, 8, 9, 1);

        ClassSession onDay = buildSession(1L, teacher, monday);
        ClassSession offDay = buildSession(2L, teacher, tuesday);

        DisruptionConstraintFact fact = new DisruptionConstraintFact(
            com.arare.features.impact.DisruptionType.TEACHER_UNAVAILABLE, 1L, "MONDAY");

        constraintVerifier.verifyThat(TimetableConstraintProvider::disruptionViolation)
            .given(fact, onDay, offDay)
            .penalizesBy(1);
    }

    @Test
    void disruptionTimeslotBlockedPenalizesSessionInThatSlot() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);
        Timeslot blocked = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot free = buildTimeslot(11L, SchoolDay.TUESDAY, 8, 9, 1);

        ClassSession inBlocked = buildSession(1L, teacher, blocked);
        ClassSession inFree = buildSession(2L, teacher, free);

        DisruptionConstraintFact fact = new DisruptionConstraintFact(
            com.arare.features.impact.DisruptionType.TIMESLOT_BLOCKED, 10L, null);

        constraintVerifier.verifyThat(TimetableConstraintProvider::disruptionViolation)
            .given(fact, inBlocked, inFree)
            .penalizesBy(1);
    }

    @Test
    void disruptionSessionCancelledPenalizesPlacedSession() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);
        Timeslot slot = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);

        ClassSession cancelledPlaced = buildSession(1L, teacher, slot);
        ClassSession cancelledUnplaced = buildSession(2L, teacher, null);

        DisruptionConstraintFact fact = new DisruptionConstraintFact(
            com.arare.features.impact.DisruptionType.SESSION_CANCELLED, 1L, null);

        constraintVerifier.verifyThat(TimetableConstraintProvider::disruptionViolation)
            .given(fact, cancelledPlaced, cancelledUnplaced)
            .penalizesBy(1);
    }

    @Test
    void disruptionSpecialEventPenalizesAllSessionsOnThatDay() {
        Teacher teacher = Teacher.builder().build();
        teacher.setId(1L);
        Timeslot monday = buildTimeslot(10L, SchoolDay.MONDAY, 8, 9, 1);
        Timeslot tuesday = buildTimeslot(11L, SchoolDay.TUESDAY, 8, 9, 1);

        ClassSession mondaySession = buildSession(1L, teacher, monday);
        ClassSession tuesdaySession = buildSession(2L, teacher, tuesday);

        DisruptionConstraintFact fact = new DisruptionConstraintFact(
            com.arare.features.impact.DisruptionType.SPECIAL_EVENT, null, "MONDAY");

        constraintVerifier.verifyThat(TimetableConstraintProvider::disruptionViolation)
            .given(fact, mondaySession, tuesdaySession)
            .penalizesBy(1);
    }

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
