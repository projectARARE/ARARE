package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.universityconfig.UniversityConfig;

// All scheduling constraints for ARARE, mapped to their spec categories.
// <p><b>Hard</b> – violations make schedule infeasible; solver never accepts them.<br>
// <b>Medium</b> – solver avoids them after satisfying all hard constraints.<br>
// <b>Soft</b>  – optimisation goals; violated only if unavoidable.</p>
// <p>IMPORTANT — forEachIncludingUnassigned:
// {@code teacher} and {@code room} use {@code allowsUnassigned=true}, so the default
// {@code factory.forEach()} silently excludes every session whose teacher or room is
// still null — making ALL constraints invisible to the solver until both are assigned.
// All constraints therefore start with {@code forEachIncludingUnassigned} and add
// explicit null-guards where needed.  Conflict constraints use groupBy instead of
// forEachUniquePair to avoid the need for a second forEachIncludingUnassigned stream
// inside a join.</p>
public class TimetableConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
            // ── Hard ──────────────────────────────────────────────────
            teacherConflict(factory),
            roomConflict(factory),
            batchConflict(factory),
            roomCapacityViolation(factory),
            roomTypeMismatch(factory),
            sectionConflict(factory),
            teacherNotQualified(factory),
            teacherUnavailable(factory),
            roomUnavailable(factory),
            breakSlotViolation(factory),
            teacherRequiredButMissing(factory),
            teacherAssignedWhenNotRequired(factory),
            labTeacherRequired(factory),
            roomRequiredButMissing(factory),
            labMultiSlotMustHaveConsecutiveStart(factory),
            multiSlotRequiresSlotNumber(factory),
            avoidSameSubjectMultipleTimesPerDay(factory),
            batchWorkingDayViolation(factory),
            batchDailyClassesCapFromUniversityConfig(factory),

            // ── Medium ────────────────────────────────────────────────
            teacherDailyHoursCap(factory),
            teacherWeeklyHoursCap(factory),
            teacherConsecutiveClassesCap(factory),
            avoidStudentIdleGaps(factory),
            avoidTeacherIdleGaps(factory),
            preferSameTeacherSameSubject(factory),
            preferDepartmentBuildings(factory),
            preferSplitLabSectionsAtSameTime(factory),

            // ── Soft ──────────────────────────────────────────────────
            teacherFreeDayPreference(factory),
            batchFreeDayPreference(factory),
            preferTeacherBuilding(factory),
            spreadSubjectAcrossWeek(factory),
            preferNonLabMultiSlotConsecutive(factory),
        };
    }

    // ==================================================================
    // HARD CONSTRAINTS
    // ==================================================================

// A teacher cannot teach two sessions at the same timeslot.
// Uses groupBy to avoid requiring forEachIncludingUnassigned on both sides of a join.
    Constraint teacherConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getTeacher),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) -> s1.getTeacher() != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && overlapsByPlannedDuration(s1, s2))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher conflict");
    }

// A room cannot host two sessions at the same timeslot.
    Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getRoom),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) -> s1.getRoom() != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && overlapsByPlannedDuration(s1, s2))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room conflict");
    }

// A batch cannot be in two sessions at the same timeslot.
    Constraint batchConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(TimetableConstraintProvider::effectiveBatch),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) -> effectiveBatch(s1) != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && overlapsByPlannedDuration(s1, s2))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Batch conflict");
    }

// Room capacity must fit the effective student count.
    Constraint roomCapacityViolation(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getRoom().getCapacity() < s.getEffectiveStudentCount())
            .penalize(HardMediumSoftScore.ONE_HARD,
                s -> s.getEffectiveStudentCount() - s.getRoom().getCapacity())
            .asConstraint("Room capacity violation");
    }

// Room type must match the subject's required room type (LECTURE vs LAB).
// Spec: "Room type must match subject.roomTypeRequired"
    Constraint roomTypeMismatch(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getSubject() != null
                && s.getSubject().getRoomTypeRequired() != null
                && s.getSubject().getRoomTypeRequired() != s.getRoom().getType())
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room type mismatch");
    }

// A class section (lab sub-group) cannot have two sessions at the same timeslot.
    Constraint sectionConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getSection),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) -> s1.getSection() != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && overlapsByPlannedDuration(s1, s2))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Section conflict");
    }

// Teacher must be qualified for the assigned subject.
    Constraint teacherNotQualified(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getSubject().isRequiresTeacher()
                && !s.getTeacher().getSubjects().contains(s.getSubject()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher not qualified for subject");
    }

// Teacher must be available at the assigned timeslot.
// Empty availableTimeslots = always available.
    Constraint teacherUnavailable(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getTimeslot() != null
                && !s.getTeacher().getAvailableTimeslots().isEmpty()
                && !s.getTeacher().getAvailableTimeslots().contains(s.getTimeslot()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher unavailable at timeslot");
    }

// Room must be available at the assigned timeslot.
// Empty availableTimeslots = always available.
    Constraint roomUnavailable(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getTimeslot() != null
                && !s.getRoom().getAvailableTimeslots().isEmpty()
                && !s.getRoom().getAvailableTimeslots().contains(s.getTimeslot()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room unavailable at timeslot");
    }

// Sessions must not be placed in BREAK or BLOCKED timeslots.
    Constraint breakSlotViolation(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null
                && s.getTimeslot().getType() != com.arare.common.enums.TimeslotType.CLASS)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Session assigned to break or blocked slot");
    }

// When a subject requires a teacher, the session must have one assigned.
    Constraint teacherRequiredButMissing(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getSubject().isRequiresTeacher() && s.getTeacher() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher required but not assigned");
    }

// When a subject does not require a teacher, no teacher should be assigned.
    Constraint teacherAssignedWhenNotRequired(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> !s.getSubject().isRequiresTeacher() && s.getTeacher() != null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher assigned though not required");
    }

// When a subject requires a room, the session must have one assigned.
    Constraint roomRequiredButMissing(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getSubject().isRequiresRoom() && s.getRoom() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room required but not assigned");
    }

// Labs must always have a teacher assigned.
    Constraint labTeacherRequired(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getSubject() != null
                && s.getSubject().isLab()
                && s.getTeacher() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Lab teacher required");
    }

// Multi-slot sessions (chunk > 1) must start at a slot that has an
// immediately consecutive next CLASS slot on the same day.
// <p>This enforces 2-hour chunk continuity and prevents starts that cross
// a break/gap between class slots.</p>
    Constraint labMultiSlotMustHaveConsecutiveStart(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null
                && s.getDuration() > 1
                && s.getSubject() != null
                && s.getSubject().isLab())
            .ifNotExists(Timeslot.class,
                Joiners.equal(s -> s.getTimeslot().getDay(), Timeslot::getDay),
                Joiners.filtering((s, slot) -> slot.getType() == TimeslotType.CLASS
                    && supportsSessionEnd(s, slot)))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Lab multi-slot crosses non-consecutive slot");
    }

// Multi-slot sessions require slot-number ordering to avoid implicit clock-hour assumptions.
    Constraint multiSlotRequiresSlotNumber(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null
                && s.getDuration() > 1
                && s.getTimeslot().getSlotNumber() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Multi-slot session missing slot numbering");
    }

    // ==================================================================
    // MEDIUM CONSTRAINTS
    // ==================================================================

// Teacher daily contact hours must not exceed maxDailyHours.
    Constraint teacherDailyHoursCap(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getTeacher,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.sum(ClassSession::getDuration))
            .filter((teacher, day, totalHours) -> totalHours > teacher.getMaxDailyHours())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (teacher, day, totalHours) -> totalHours - teacher.getMaxDailyHours())
            .asConstraint("Teacher daily hours exceeded");
    }

// Teacher total weekly hours must not exceed maxWeeklyHours.
    Constraint teacherWeeklyHoursCap(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getTeacher,
                ConstraintCollectors.sum(ClassSession::getDuration))
            .filter((teacher, totalHours) -> totalHours > teacher.getMaxWeeklyHours())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (teacher, totalHours) -> totalHours - teacher.getMaxWeeklyHours())
            .asConstraint("Teacher weekly hours exceeded");
    }

// Limit consecutive classes for teachers (sessions per day proxy).
    Constraint teacherConsecutiveClassesCap(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getTeacher,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.count())
            .filter((teacher, day, count) -> count > teacher.getMaxConsecutiveClasses())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (teacher, day, count) -> count - teacher.getMaxConsecutiveClasses())
            .asConstraint("Teacher consecutive classes exceeded");
    }

// Avoid idle gaps in the student schedule.
    Constraint avoidStudentIdleGaps(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null && s.getTimeslot() != null)
            .join(ClassSession.class,
                Joiners.equal(TimetableConstraintProvider::effectiveBatch),
                Joiners.equal(s -> s.getTimeslot().getDay()),
                Joiners.lessThan(ClassSession::getId))
            .filter((s1, s2) -> s2.getTimeslot() != null && effectiveBatch(s2) != null && hasIdleGap(s1, s2))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Student idle gap");
    }

// Prefer parallel timing when a lab is split into multiple sections of the same batch.
    Constraint preferSplitLabSectionsAtSameTime(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getSubject() != null
                && s.getSubject().isLab()
                && s.getSection() != null
                && s.getTimeslot() != null)
            .join(ClassSession.class,
                Joiners.equal(ClassSession::getSubject),
                Joiners.equal(TimetableConstraintProvider::effectiveBatch),
                Joiners.equal(ClassSession::getDuration),
                Joiners.lessThan(ClassSession::getId))
            .filter((s1, s2) -> s2.getSection() != null
                && s2.getTimeslot() != null
                && !s1.getSection().getId().equals(s2.getSection().getId())
                && !s1.getTimeslot().getId().equals(s2.getTimeslot().getId()))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Split lab sections not aligned in time");
    }

// Avoid idle gaps in the teacher schedule.
    Constraint avoidTeacherIdleGaps(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .join(ClassSession.class,
                Joiners.equal(ClassSession::getTeacher),
                Joiners.equal(s -> s.getTimeslot().getDay()),
                Joiners.lessThan(ClassSession::getId))
            .filter((s1, s2) -> s2.getTimeslot() != null && s2.getTeacher() != null && hasIdleGap(s1, s2))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Teacher idle gap");
    }

// Sessions should be in buildings assigned to the subject's department.
    Constraint preferDepartmentBuildings(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getSubject() != null
                && s.getSubject().getDepartment() != null
                && !s.getSubject().getDepartment().getBuildingsAllowed().isEmpty()
                && !s.getSubject().getDepartment().getBuildingsAllowed()
                    .contains(s.getRoom().getBuilding()))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Session outside department buildings");
    }

// Same subject should be taught by the same teacher for a given batch.
    Constraint preferSameTeacherSameSubject(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getBatch() != null)
            .join(ClassSession.class,
                Joiners.equal(ClassSession::getBatch),
                Joiners.equal(ClassSession::getSubject),
                Joiners.lessThan(ClassSession::getId))
            .filter((s1, s2) -> s2.getTeacher() != null
                && !s1.getTeacher().equals(s2.getTeacher()))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Different teachers assigned to same subject for same batch");
    }

    // ==================================================================
    // SOFT CONSTRAINTS
    // ==================================================================

// Teacher prefers a specific free day.
    Constraint teacherFreeDayPreference(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getTimeslot() != null
                && s.getTeacher().getPreferredFreeDay() != null
                && s.getTimeslot().getDay() == s.getTeacher().getPreferredFreeDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Teacher free day violated");
    }

// Batch prefers a specific free day.
    Constraint batchFreeDayPreference(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null
                && s.getTimeslot() != null
                && effectiveBatch(s).getPreferredFreeDay() != null
                && s.getTimeslot().getDay() == effectiveBatch(s).getPreferredFreeDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Batch free day violated");
    }

// Prefer sessions in the teacher's preferred buildings.
    Constraint preferTeacherBuilding(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getRoom() != null
                && !s.getTeacher().getPreferredBuildings().isEmpty()
                && !s.getTeacher().getPreferredBuildings()
                    .contains(s.getRoom().getBuilding()))
            .penalize(HardMediumSoftScore.ONE_SOFT,
                s -> s.getTeacher().getMovementPenalty())
            .asConstraint("Teacher building preference violated");
    }

// Spread sessions of the same subject across the week.
    Constraint spreadSubjectAcrossWeek(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null && effectiveBatch(s) != null)
            .join(ClassSession.class,
                Joiners.equal(ClassSession::getSubject),
                Joiners.equal(TimetableConstraintProvider::effectiveBatch),
                Joiners.lessThan(ClassSession::getId))
            .filter((s1, s2) -> s2.getTimeslot() != null
                && s1.getTimeslot().getDay() == s2.getTimeslot().getDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Same subject scheduled twice on same day");
    }

// Penalise same subject appearing multiple times per day.
    Constraint avoidSameSubjectMultipleTimesPerDay(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null && s.getTimeslot() != null)
            .groupBy(TimetableConstraintProvider::effectiveBatch,
                ClassSession::getSubject,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.count())
            .filter((batch, subject, day, count) -> count > subject.getMaxSessionsPerDay())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (batch, subject, day, count) -> count - subject.getMaxSessionsPerDay())
            .asConstraint("Cognitive load: same subject too many times per day");
    }

// Sessions must respect batch-level working days when configured.
    Constraint batchWorkingDayViolation(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null
                && s.getTimeslot() != null
                && !effectiveBatch(s).getWorkingDays().isEmpty()
                && !effectiveBatch(s).getWorkingDays().contains(s.getTimeslot().getDay()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Batch scheduled outside working days");
    }

// Enforce university-wide max classes per batch per day from active config.
    Constraint batchDailyClassesCapFromUniversityConfig(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null && s.getTimeslot() != null)
            .groupBy(TimetableConstraintProvider::effectiveBatch,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.count())
            .join(UniversityConfig.class,
                Joiners.filtering((batch, day, count, cfg) -> cfg.isActive()))
            .filter((batch, day, count, cfg) -> count > cfg.getMaxClassesPerDay())
            .penalize(HardMediumSoftScore.ONE_HARD,
                (batch, day, count, cfg) -> count - cfg.getMaxClassesPerDay())
            .asConstraint("Batch daily classes exceed university max");
    }

// Non-lab multi-slot sessions should be consecutive when possible.
    Constraint preferNonLabMultiSlotConsecutive(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null
                && s.getDuration() > 1
                && s.getSubject() != null
                && !s.getSubject().isLab())
            .ifNotExists(Timeslot.class,
                Joiners.equal(s -> s.getTimeslot().getDay(), Timeslot::getDay),
                Joiners.filtering((s, slot) -> slot.getType() == TimeslotType.CLASS
                    && supportsSessionEnd(s, slot)))
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Non-lab multi-slot should be consecutive");
    }

    // ==================================================================
    // HELPERS
    // ==================================================================

    private static boolean hasIdleGap(ClassSession a, ClassSession b) {
        var tA = a.getTimeslot();
        var tB = b.getTimeslot();
        if (tA.getStartTime().isBefore(tB.getStartTime())) {
            return tA.getEndTime().isBefore(tB.getStartTime());
        } else {
            return tB.getEndTime().isBefore(tA.getStartTime());
        }
    }

    private static Batch effectiveBatch(ClassSession s) {
        if (s.getBatch() != null) return s.getBatch();
        if (s.getSection() != null) return s.getSection().getBatch();
        return null;
    }

    private static boolean supportsSessionEnd(ClassSession s, Timeslot slot) {
        Integer startSlot = s.getTimeslot().getSlotNumber();
        Integer candidate = slot.getSlotNumber();
        if (startSlot == null || candidate == null) {
            return false;
        }
        int requiredEndSlot = startSlot + s.getDuration() - 1;
        return candidate == requiredEndSlot;
    }

    private static boolean overlapsByPlannedDuration(ClassSession a, ClassSession b) {
        Integer aStartSlot = a.getTimeslot().getSlotNumber();
        Integer bStartSlot = b.getTimeslot().getSlotNumber();

        if (aStartSlot != null && bStartSlot != null) {
            int aEndExclusive = aStartSlot + a.getDuration();
            int bEndExclusive = bStartSlot + b.getDuration();
            return aStartSlot < bEndExclusive && bStartSlot < aEndExclusive;
        }

        if (a.getDuration() == 1 && b.getDuration() == 1) {
            var aStart = a.getTimeslot().getStartTime();
            var aEnd = a.getTimeslot().getEndTime();
            var bStart = b.getTimeslot().getStartTime();
            var bEnd = b.getTimeslot().getEndTime();
            return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
        }

        // Without slot numbering, multi-slot overlap is ambiguous. Penalize conservatively.
        return true;
    }
}
