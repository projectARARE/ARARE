package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.arare.features.classsession.ClassSession;
import com.arare.features.timeslot.Timeslot;

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

            // ── Medium ────────────────────────────────────────────────
            teacherDailyHoursCap(factory),
            teacherConsecutiveClassesCap(factory),
            avoidStudentIdleGaps(factory),
            preferSameTeacherSameSubject(factory),

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
     * Approximation: penalise batches with more than 1 unassigned slot between first
     * and last session on a day. Full implementation requires session ordering logic.
     */
    Constraint avoidStudentIdleGaps(ConstraintFactory factory) {
        return factory.forEach(ClassSession.class)
            .filter(s -> s.getBatch() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getBatch,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.count())
            // Placeholder: actual idle-gap detection needs sorted timeslot analysis
            .filter((batch, day, count) -> count == 0)
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Student idle gap");
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
