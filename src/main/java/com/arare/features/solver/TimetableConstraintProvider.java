package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.arare.features.classsession.ClassSession;

/**
 * All scheduling constraints for ARARE, mapped to their spec categories.
 *
 * <p><b>Hard</b> – violations make schedule infeasible; solver never accepts them.<br>
 * <b>Medium</b> – solver avoids them after satisfying all hard constraints.<br>
 * <b>Soft</b>  – optimisation goals; violated only if unavoidable.</p>
 *
 * <p>Each method documents its spec origin and penalty formula.</p>
 */
public class TimetableConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
            // ── Hard ──────────────────────────────────────────────────
            teacherConflict(factory),
            roomConflict(factory),
            batchConflict(factory),
            roomCapacityViolation(factory),
            teacherNotQualified(factory),
            teacherUnavailable(factory),
            roomUnavailable(factory),
            breakSlotViolation(factory),
            labSessionsMustUseSections(factory),
            teacherRequiredButMissing(factory),
            roomRequiredButMissing(factory),

            // ── Medium ────────────────────────────────────────────────
            teacherDailyHoursCap(factory),
            teacherWeeklyHoursCap(factory),
            teacherConsecutiveClassesCap(factory),
            avoidStudentIdleGaps(factory),
            avoidTeacherIdleGaps(factory),
            preferSameTeacherSameSubject(factory),
            preferDepartmentBuildings(factory),

            // ── Soft ──────────────────────────────────────────────────
            teacherFreeDayPreference(factory),
            batchFreeDayPreference(factory),
            preferTeacherBuilding(factory),
            spreadSubjectAcrossWeek(factory),
            avoidSameSubjectMultipleTimesPerDay(factory),
        };
    }

    // ==================================================================
    // HARD CONSTRAINTS
    // ==================================================================

    /**
     * A teacher cannot teach two sessions at the same timeslot.
     * Spec: "Teacher cannot teach two classes simultaneously"
     */
    Constraint teacherConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getTeacher),
                Joiners.equal(ClassSession::getTimeslot))
            .filter((s1, s2) -> s1.getTeacher() != null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher conflict");
    }

    /**
     * A room cannot host two sessions at the same timeslot.
     * Spec: "Room cannot host multiple sessions simultaneously"
     */
    Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getRoom),
                Joiners.equal(ClassSession::getTimeslot))
            .filter((s1, s2) -> s1.getRoom() != null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room conflict");
    }

    /**
     * A batch (student group) cannot be in two sessions at the same timeslot.
     * Spec: implied by "class" hard constraints.
     */
    Constraint batchConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getBatch),
                Joiners.equal(ClassSession::getTimeslot))
            .filter((s1, s2) -> s1.getBatch() != null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Batch conflict");
    }

    /**
     * Room capacity must fit the effective student count (batch or section size).
     * Spec: "Room capacity must fit class size"
     */
    Constraint roomCapacityViolation(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getRoom().getCapacity() < s.getEffectiveStudentCount())
            .penalize(HardMediumSoftScore.ONE_HARD,
                s -> s.getEffectiveStudentCount() - s.getRoom().getCapacity())
            .asConstraint("Room capacity violation");
    }

    /**
     * Teacher must be qualified for the assigned subject.
     * Spec: "Teacher must be qualified for subject"
     */
    Constraint teacherNotQualified(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getSubject().isRequiresTeacher()
                && !s.getTeacher().getSubjects().contains(s.getSubject()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher not qualified for subject");
    }

    /**
     * Teacher must be available at the assigned timeslot.
     * Spec: "Teacher must be available"
     * Note: empty availableTimeslots = available all the time (no restriction).
     */
    Constraint teacherUnavailable(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getTimeslot() != null
                && !s.getTeacher().getAvailableTimeslots().isEmpty()
                && !s.getTeacher().getAvailableTimeslots().contains(s.getTimeslot()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher unavailable at timeslot");
    }

    /**
     * Room must be available at the assigned timeslot.
     * Spec: "Room must be available"
     */
    Constraint roomUnavailable(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getTimeslot() != null
                && !s.getRoom().getAvailableTimeslots().isEmpty()
                && !s.getRoom().getAvailableTimeslots().contains(s.getTimeslot()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room unavailable at timeslot");
    }

    /**
     * Sessions must not be placed in BREAK or BLOCKED timeslots.
     * Spec: "Timeslot must not be blocked" / "Break timeslots must never contain classes"
     */
    Constraint breakSlotViolation(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTimeslot() != null
                && s.getTimeslot().getType() != com.arare.common.enums.TimeslotType.CLASS)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Session assigned to break or blocked slot");
    }

    /**
     * Lab sessions (subject.isLab == true) must be assigned to a ClassSection, not a full Batch.
     * Spec: "Lab sessions must use sections"
     *
     * <p>When a batch has 60 students but the lab capacity is 36, the session must be split
     * into sections. A lab ClassSession that still holds a full batch is a configuration error.</p>
     */
    Constraint labSessionsMustUseSections(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getSubject() != null
                && s.getSubject().isLab()
                && s.getSection() == null   // lab session has no section assigned
                && s.getBatch() != null)    // but has a full batch (wrong)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Lab session must use ClassSection, not full Batch");
    }

    /**
     * When a subject requires a teacher, the session must have one assigned.
     * Spec: "Teacher must be assigned when subject.requiresTeacher == true"
     */
    Constraint teacherRequiredButMissing(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getSubject().isRequiresTeacher() && s.getTeacher() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher required but not assigned");
    }

    /**
     * When a subject requires a room, the session must have one assigned.
     * Spec: "Room must be assigned when subject.requiresRoom == true"
     */
    Constraint roomRequiredButMissing(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getSubject().isRequiresRoom() && s.getRoom() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room required but not assigned");
    }

    // ==================================================================
    // MEDIUM CONSTRAINTS
    // ==================================================================

    /**
     * Teacher daily contact hours must not exceed maxDailyHours.
     * Spec: "Teacher max daily hours"
     */
    Constraint teacherDailyHoursCap(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getTeacher,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.sum(ClassSession::getDuration))
            .filter((teacher, day, totalHours) -> totalHours > teacher.getMaxDailyHours())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (teacher, day, totalHours) -> totalHours - teacher.getMaxDailyHours())
            .asConstraint("Teacher daily hours exceeded");
    }

    /**
     * Teacher total weekly hours must not exceed maxWeeklyHours.
     * Spec: "Teacher max weekly hours"
     */
    Constraint teacherWeeklyHoursCap(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getTeacher,
                ConstraintCollectors.sum(ClassSession::getDuration))
            .filter((teacher, totalHours) -> totalHours > teacher.getMaxWeeklyHours())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (teacher, totalHours) -> totalHours - teacher.getMaxWeeklyHours())
            .asConstraint("Teacher weekly hours exceeded");
    }

    /**
     * Limit consecutive classes for teachers.
     * Spec: "Teacher max consecutive classes" / "Max Consecutive Classes Constraint"
     * Implementation note: full consecutive-slot detection requires custom stream;
     * this simplified version acts as a proxy by capping sessions per day.
     * Replace with a more sophisticated gap analysis during refinement.
     */
    Constraint teacherConsecutiveClassesCap(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getTeacher,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.count())
            .filter((teacher, day, count) -> count > teacher.getMaxConsecutiveClasses())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (teacher, day, count) -> count - teacher.getMaxConsecutiveClasses())
            .asConstraint("Teacher consecutive classes exceeded");
    }

    /**
     * Avoid idle gaps in the student schedule.
     * Spec: "Avoid student idle gaps"
     *
     * <p>Penalises any pair of sessions for the same batch on the same day where
     * one session ends strictly before the other begins — i.e., there is dead time
     * between them that a student must sit through with no class.</p>
     *
     * <p>Example (gap):     09:00–10:00, then 12:00–13:00 → penalised.<br>
     * Example (no gap):   09:00–10:00, then 10:00–11:00 → not penalised.</p>
     *
     * <p>Note: This pairwise approach is a known approximation. For three contiguous
     * sessions A-B-C, the pair (A,C) is also flagged because A ends before C starts.
     * The solver minimises total penalties and will still prefer compact schedules.</p>
     */
    Constraint avoidStudentIdleGaps(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getBatch),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) ->
                s1.getBatch() != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && hasIdleGap(s1, s2))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Student idle gap");
    }

    /**
     * Returns true when there is a strict time gap between two sessions on the same day
     * (i.e., the earlier session ends before the later session starts, with dead time between them).
     */
    private static boolean hasIdleGap(ClassSession a, ClassSession b) {
        var tA = a.getTimeslot();
        var tB = b.getTimeslot();
        if (tA.getStartTime().isBefore(tB.getStartTime())) {
            // a comes first — gap exists if a's end < b's start
            return tA.getEndTime().isBefore(tB.getStartTime());
        } else {
            // b comes first — gap exists if b's end < a's start
            return tB.getEndTime().isBefore(tA.getStartTime());
        }
    }

    /**
     * Avoid idle gaps in the teacher schedule.
     * Spec: "Avoid teacher idle gaps"
     *
     * <p>Penalises any pair of sessions for the same teacher on the same day where
     * there is dead time between them (teacher is on-site but not teaching).</p>
     */
    Constraint avoidTeacherIdleGaps(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getTeacher),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) ->
                s1.getTeacher() != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && hasIdleGap(s1, s2))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Teacher idle gap");
    }

    /**
     * Sessions should be placed in buildings assigned to the subject's department.
     * Spec: "Prefer department buildings"
     *
     * <p>Penalises any session whose room's building is not in the subject
     * department's {@code buildingsAllowed} list. No penalty when the list is empty
     * (department has no building restriction).</p>
     */
    Constraint preferDepartmentBuildings(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getSubject() != null
                && s.getSubject().getDepartment() != null
                && !s.getSubject().getDepartment().getBuildingsAllowed().isEmpty()
                && !s.getSubject().getDepartment().getBuildingsAllowed()
                    .contains(s.getRoom().getBuilding()))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Session outside department buildings");
    }

    /**
     * Same subject should be taught by the same teacher across all sessions.
     * Spec: "Prefer same teacher for same subject"
     */
    Constraint preferSameTeacherSameSubject(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getBatch),
                Joiners.equal(ClassSession::getSubject))
            .filter((s1, s2) -> s1.getTeacher() != null
                && s2.getTeacher() != null
                && !s1.getTeacher().equals(s2.getTeacher()))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Different teachers assigned to same subject for same batch");
    }

    // ==================================================================
    // SOFT CONSTRAINTS
    // ==================================================================

    /**
     * Teacher prefers a specific free day.
     * Spec: "Prefer free day" / "preferredFreeDay"
     */
    Constraint teacherFreeDayPreference(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getTimeslot() != null
                && s.getTeacher().getPreferredFreeDay() != null
                && s.getTimeslot().getDay() == s.getTeacher().getPreferredFreeDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Teacher free day violated");
    }

    /**
     * Batch prefers a specific free day.
     * Spec: "Prefer free day" for students.
     */
    Constraint batchFreeDayPreference(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getBatch() != null
                && s.getTimeslot() != null
                && s.getBatch().getPreferredFreeDay() != null
                && s.getTimeslot().getDay() == s.getBatch().getPreferredFreeDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Batch free day violated");
    }

    /**
     * Prefer sessions in the teacher's preferred buildings.
     * Spec: "Prefer teacher building preference" / "Teacher Movement Constraint"
     */
    Constraint preferTeacherBuilding(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getRoom() != null
                && !s.getTeacher().getPreferredBuildings().isEmpty()
                && !s.getTeacher().getPreferredBuildings()
                    .contains(s.getRoom().getBuilding()))
            .penalize(HardMediumSoftScore.ONE_SOFT,
                s -> s.getTeacher().getMovementPenalty())
            .asConstraint("Teacher building preference violated");
    }

    /**
     * Spread sessions of the same subject across the week.
     * Spec: "Spread subject sessions across week" / "Lecture Distribution Constraint"
     * Penalises having more than one session of the same subject on the same day.
     */
    Constraint spreadSubjectAcrossWeek(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getSubject),
                Joiners.equal(ClassSession::getBatch))
            .filter((s1, s2) -> s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && s1.getTimeslot().getDay() == s2.getTimeslot().getDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Same subject scheduled twice on same day");
    }

    /**
     * Penalise same subject appearing multiple times per day.
     * Spec: "Avoid same subject multiple times in a day" / Student Cognitive Load Constraint.
     * This overlaps with spreadSubjectAcrossWeek but scores consecutive occurrences higher.
     */
    Constraint avoidSameSubjectMultipleTimesPerDay(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getBatch() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getBatch,
                ClassSession::getSubject,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.count())
            .filter((batch, subject, day, count) -> count > subject.getMaxSessionsPerDay())
            .penalize(HardMediumSoftScore.ONE_SOFT,
                (batch, subject, day, count) -> count - subject.getMaxSessionsPerDay())
            .asConstraint("Cognitive load: same subject too many times per day");
    }
}
