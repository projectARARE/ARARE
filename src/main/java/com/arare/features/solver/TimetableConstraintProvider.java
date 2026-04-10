package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.universityconfig.UniversityConfig;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TimetableConstraintProvider implements ConstraintProvider {

    private static final LocalTime MIDDAY_BREAK_START = LocalTime.of(12, 0);
    private static final LocalTime MIDDAY_BREAK_END = LocalTime.of(14, 0);

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
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

            teacherDailyHoursCap(factory),
            teacherWeeklyHoursCap(factory),
            teacherConsecutiveClassesCap(factory),
            mandatoryBatchBreak(factory),
            avoidStudentIdleGaps(factory),
            avoidTeacherIdleGaps(factory),
            preferSameTeacherSameSubject(factory),
            preferDepartmentBuildings(factory),
            minimizeTeacherBuildingChanges(factory),
            preferSplitLabSectionsAtSameTime(factory),

            teacherFreeDayPreference(factory),
            batchFreeDayPreference(factory),
            preferTeacherBuilding(factory),
            minimizeBatchBuildingChanges(factory),
            roomStability(factory),
            spreadSubjectAcrossWeek(factory),
            preferNonLabMultiSlotConsecutive(factory),
        };
    }


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

    Constraint roomCapacityViolation(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getRoom().getCapacity() < s.getEffectiveStudentCount())
            .penalize(HardMediumSoftScore.ONE_HARD,
                s -> s.getEffectiveStudentCount() - s.getRoom().getCapacity())
            .asConstraint("Room capacity violation");
    }

    Constraint roomTypeMismatch(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getSubject() != null
                && !roomMatchesSubjectRequirements(s))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room type mismatch");
    }

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

    Constraint teacherNotQualified(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getSubject().isRequiresTeacher()
                && !s.getTeacher().getSubjects().contains(s.getSubject()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher not qualified for subject");
    }

    Constraint teacherUnavailable(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getTimeslot() != null
                && !s.getTeacher().getAvailableTimeslots().isEmpty()
                && !s.getTeacher().getAvailableTimeslots().contains(s.getTimeslot()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher unavailable at timeslot");
    }

    Constraint roomUnavailable(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null
                && s.getTimeslot() != null
                && !s.getRoom().getAvailableTimeslots().isEmpty()
                && !s.getRoom().getAvailableTimeslots().contains(s.getTimeslot()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room unavailable at timeslot");
    }

    Constraint breakSlotViolation(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null
                && s.getTimeslot().getType() != com.arare.common.enums.TimeslotType.CLASS)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Session assigned to break or blocked slot");
    }

    Constraint teacherRequiredButMissing(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getSubject().isRequiresTeacher() && s.getTeacher() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher required but not assigned");
    }

    Constraint teacherAssignedWhenNotRequired(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> !s.getSubject().isRequiresTeacher() && s.getTeacher() != null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Teacher assigned though not required");
    }

    Constraint roomRequiredButMissing(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getSubject().isRequiresRoom() && s.getRoom() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Room required but not assigned");
    }

    Constraint labTeacherRequired(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getSubject() != null
                && s.getSubject().isLab()
                && s.getTeacher() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Lab teacher required");
    }

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

    Constraint multiSlotRequiresSlotNumber(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null
                && s.getDuration() > 1
                && s.getTimeslot().getSlotNumber() == null)
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Multi-slot session missing slot numbering");
    }


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

    Constraint teacherConsecutiveClassesCap(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null && s.getTimeslot() != null)
            .groupBy(ClassSession::getTeacher,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.toList())
            .filter((teacher, day, sessions) ->
                consecutiveSlotExcess(sessions, teacher.getMaxConsecutiveClasses()) > 0)
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (teacher, day, sessions) ->
                    consecutiveSlotExcess(sessions, teacher.getMaxConsecutiveClasses()))
            .asConstraint("Teacher consecutive classes exceeded");
    }

    Constraint mandatoryBatchBreak(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null && s.getTimeslot() != null)
            .groupBy(TimetableConstraintProvider::effectiveBatch,
                s -> s.getTimeslot().getDay(),
                ConstraintCollectors.toList())
            .filter((batch, day, sessions) -> !hasMiddayBreak(sessions))
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Batch missing midday break");
    }

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


    Constraint teacherFreeDayPreference(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTeacher() != null
                && s.getTimeslot() != null
                && s.getTeacher().getPreferredFreeDay() != null
                && s.getTimeslot().getDay() == s.getTeacher().getPreferredFreeDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Teacher free day violated");
    }

    Constraint batchFreeDayPreference(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null
                && s.getTimeslot() != null
                && effectiveBatch(s).getPreferredFreeDay() != null
                && s.getTimeslot().getDay() == effectiveBatch(s).getPreferredFreeDay())
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Batch free day violated");
    }

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

    Constraint spreadSubjectAcrossWeek(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getTimeslot() != null && effectiveBatch(s) != null)
            .join(ClassSession.class,
                Joiners.equal(ClassSession::getSubject),
                Joiners.equal(TimetableConstraintProvider::effectiveBatch),
                Joiners.lessThan(ClassSession::getId))
            .filter((s1, s2) -> s2.getTimeslot() != null
                && s1.getTimeslot().getDay() == s2.getTimeslot().getDay())
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("Same subject scheduled twice on same day");
    }

    Constraint minimizeTeacherBuildingChanges(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(ClassSession::getTeacher),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) -> s1.getTeacher() != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && s1.getRoom() != null
                && s2.getRoom() != null
                && s1.getRoom().getBuilding() != null
                && s2.getRoom().getBuilding() != null
                && areBackToBackBySlotNumber(s1, s2)
                && !s1.getRoom().getBuilding().equals(s2.getRoom().getBuilding()))
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                (s1, s2) -> Math.max(1, s1.getTeacher().getMovementPenalty()))
            .asConstraint("Teacher building change between consecutive classes");
    }

    Constraint minimizeBatchBuildingChanges(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                ClassSession.class,
                Joiners.equal(TimetableConstraintProvider::effectiveBatch),
                Joiners.equal(s -> s.getTimeslot() != null ? s.getTimeslot().getDay() : null))
            .filter((s1, s2) -> effectiveBatch(s1) != null
                && s1.getTimeslot() != null
                && s2.getTimeslot() != null
                && s1.getRoom() != null
                && s2.getRoom() != null
                && s1.getRoom().getBuilding() != null
                && s2.getRoom().getBuilding() != null
                && areBackToBackBySlotNumber(s1, s2)
                && !s1.getRoom().getBuilding().equals(s2.getRoom().getBuilding()))
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Batch building change between consecutive classes");
    }

    Constraint roomStability(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> s.getRoom() != null && effectiveBatch(s) != null)
            .join(ClassSession.class,
                Joiners.equal(ClassSession::getSubject),
                Joiners.equal(TimetableConstraintProvider::effectiveBatch),
                Joiners.lessThan(ClassSession::getId))
            .filter((s1, s2) -> s2.getRoom() != null
                && !s1.getRoom().equals(s2.getRoom()))
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Room stability for subject and batch");
    }

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

    Constraint batchWorkingDayViolation(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ClassSession.class)
            .filter(s -> effectiveBatch(s) != null
                && s.getTimeslot() != null
                && !effectiveBatch(s).getWorkingDays().isEmpty()
                && !effectiveBatch(s).getWorkingDays().contains(s.getTimeslot().getDay()))
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("Batch scheduled outside working days");
    }

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

    private static boolean roomMatchesSubjectRequirements(ClassSession s) {
        if (s.getRoom() == null || s.getSubject() == null) {
            return true;
        }
        if (s.getSubject().getRoomTypeRequired() != null
            && s.getSubject().getRoomTypeRequired() != s.getRoom().getType()) {
            return false;
        }
        if (s.getSubject().getLabSubtypeRequired() != null
            && s.getSubject().getLabSubtypeRequired() != s.getRoom().getLabSubtype()) {
            return false;
        }
        return true;
    }

    private static int consecutiveSlotExcess(List<ClassSession> sessions, int maxConsecutiveClasses) {
        int safeCap = Math.max(1, maxConsecutiveClasses);
        long sessionsWithoutSlotNumber = sessions.stream()
            .filter(s -> s.getTimeslot().getSlotNumber() == null)
            .count();

        int proxyExcess = Math.max(0, sessions.size() - safeCap);
        if (sessionsWithoutSlotNumber == sessions.size()) {
            return proxyExcess;
        }

        List<ClassSession> ordered = sessions.stream()
            .filter(s -> s.getTimeslot().getSlotNumber() != null)
            .sorted(Comparator.comparingInt(s -> s.getTimeslot().getSlotNumber()))
            .toList();

        int maxRunLength = 0;
        int currentRunLength = 0;
        int currentRunEnd = Integer.MIN_VALUE;

        for (ClassSession session : ordered) {
            int startSlot = session.getTimeslot().getSlotNumber();
            int endSlot = startSlot + session.getDuration() - 1;

            if (currentRunLength == 0) {
                currentRunLength = endSlot - startSlot + 1;
                currentRunEnd = endSlot;
            } else if (startSlot <= currentRunEnd + 1) {
                if (endSlot > currentRunEnd) {
                    currentRunLength += endSlot - currentRunEnd;
                    currentRunEnd = endSlot;
                }
            } else {
                maxRunLength = Math.max(maxRunLength, currentRunLength);
                currentRunLength = endSlot - startSlot + 1;
                currentRunEnd = endSlot;
            }
        }
        maxRunLength = Math.max(maxRunLength, currentRunLength);

        int slotBasedExcess = Math.max(0, maxRunLength - safeCap);
        if (sessionsWithoutSlotNumber > 0) {
            return Math.max(slotBasedExcess, proxyExcess);
        }
        return slotBasedExcess;
    }

    private static boolean isMiddayClassSlot(Timeslot timeslot) {
        return timeslot != null
            && timeslot.getType() == TimeslotType.CLASS
            && timeslot.getStartTime().isBefore(MIDDAY_BREAK_END)
            && timeslot.getEndTime().isAfter(MIDDAY_BREAK_START);
    }

    private static boolean areBackToBackBySlotNumber(ClassSession a, ClassSession b) {
        Integer aStart = a.getTimeslot().getSlotNumber();
        Integer bStart = b.getTimeslot().getSlotNumber();
        if (aStart == null || bStart == null) {
            return false;
        }

        ClassSession first = aStart <= bStart ? a : b;
        ClassSession second = first == a ? b : a;
        int firstStart = first.getTimeslot().getSlotNumber();
        int secondStart = second.getTimeslot().getSlotNumber();
        int firstEnd = firstStart + first.getDuration() - 1;
        return secondStart == firstEnd + 1;
    }

    private static boolean hasMiddayBreak(List<ClassSession> sessions) {
        int windowStart = MIDDAY_BREAK_START.toSecondOfDay();
        int windowEnd = MIDDAY_BREAK_END.toSecondOfDay();

        List<int[]> covered = new ArrayList<>();
        for (ClassSession session : sessions) {
            Timeslot timeslot = session.getTimeslot();
            if (timeslot == null || timeslot.getType() != TimeslotType.CLASS) {
                continue;
            }

            int start = timeslot.getStartTime().toSecondOfDay();
            int end = timeslot.getEndTime().toSecondOfDay();
            int overlapStart = Math.max(start, windowStart);
            int overlapEnd = Math.min(end, windowEnd);
            if (overlapStart < overlapEnd) {
                covered.add(new int[]{overlapStart, overlapEnd});
            }
        }

        if (covered.isEmpty()) {
            return true;
        }

        covered.sort(Comparator.comparingInt(a -> a[0]));
        int mergedStart = covered.get(0)[0];
        int mergedEnd = covered.get(0)[1];

        if (mergedStart > windowStart) {
            return true;
        }

        for (int i = 1; i < covered.size(); i++) {
            int[] interval = covered.get(i);
            if (interval[0] > mergedEnd) {
                return true;
            }
            mergedEnd = Math.max(mergedEnd, interval[1]);
        }
        return mergedEnd < windowEnd;
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

        return true;
    }
}
