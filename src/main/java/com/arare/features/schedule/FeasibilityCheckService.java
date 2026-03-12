package com.arare.features.schedule;

import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pre-solve feasibility validator — the Constraint Propagation layer.
 *
 * <p>Runs lightweight checks before the Timefold solver is invoked to:
 * <ol>
 *   <li>Detect hard errors that guarantee solver infeasibility (e.g. a subject
 *       with no qualified teacher).</li>
 *   <li>Surface warnings that typically produce a poor score (e.g. more sessions
 *       than available teacher-timeslot slots).</li>
 * </ol>
 *
 * <p>This is O(batches × subjects) — fast enough to run interactively
 * in the UI before clicking "Generate Schedule".</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeasibilityCheckService {

    private final BatchRepository        batchRepo;
    private final SubjectRepository      subjectRepo;
    private final TeacherRepository      teacherRepo;
    private final RoomRepository         roomRepo;
    private final TimeslotRepository     timeslotRepo;
    private final ClassSectionRepository sectionRepo;

    @Transactional(readOnly = true)
    public FeasibilityCheckResult check(ScheduleRequest req) {
        List<FeasibilityIssue> issues = new ArrayList<>();

        // ── 1. Load entities scoped to the request ────────────────────────────
        List<Batch>   batches  = loadBatches(req);
        List<Teacher> teachers = loadTeachers(req);
        List<Room>    rooms    = loadRooms(req);
        int classTimeslotCount = timeslotRepo.findByType(TimeslotType.CLASS).size();

        if (batches.isEmpty()) {
            issues.add(error("BATCH",
                    "No batches found for the selected scope. Configure batches before generating a schedule.",
                    null, null));
            return result(issues, 0, classTimeslotCount);
        }

        // Force-load lazy associations used in the checks below
        batches.forEach(b -> b.getDepartment().getId());
        teachers.forEach(t -> t.getSubjects().size());

        Set<Long> deptIds = batches.stream()
                .map(b -> b.getDepartment().getId())
                .collect(Collectors.toSet());

        List<Subject> subjects = req.departmentId() != null
                ? subjectRepo.findByDepartmentId(req.departmentId())
                : subjectRepo.findAll().stream()
                        .filter(s -> deptIds.contains(s.getDepartment().getId()))
                        .toList();

        if (subjects.isEmpty()) {
            issues.add(warn("SUBJECT",
                    "No subjects found for the selected batches' departments. Add subjects to enable scheduling.",
                    null, null));
        }

        // ── 2. No CLASS timeslots ──────────────────────────────────────────────
        if (classTimeslotCount == 0) {
            issues.add(error("TIMESLOT",
                    "No CLASS-type timeslots are configured. Add timeslots before generating a schedule.",
                    null, null));
            return result(issues, 0, 0);
        }

        // ── 3. Subject → teacher qualification check (ERROR if none) ──────────
        for (Subject s : subjects) {
            if (!s.isRequiresTeacher()) continue;
            boolean hasQualified = teachers.stream()
                    .anyMatch(t -> t.getSubjects().stream()
                            .anyMatch(ts -> ts.getId().equals(s.getId())));
            if (!hasQualified) {
                issues.add(error("TEACHER",
                        "No qualified teacher for subject: " + label(s),
                        s.getId(), s.getName()));
            }
        }

        // ── 4. Lab subject → room type check (ERROR if no room of required type)
        for (Subject s : subjects) {
            if (!s.isLab() || !s.isRequiresRoom()) continue;
            boolean hasRoom = rooms.stream()
                    .anyMatch(r -> r.getType() == s.getRoomTypeRequired());
            if (!hasRoom) {
                issues.add(error("ROOM",
                        "No " + s.getRoomTypeRequired() + " room available for lab subject: " + label(s),
                        s.getId(), s.getName()));
            }
        }

        // ── 5. Estimate total sessions & global capacity ──────────────────────
        List<Long> batchIds = batches.stream().map(Batch::getId).toList();
        List<ClassSection> sections = batchIds.isEmpty()
                ? Collections.emptyList()
                : sectionRepo.findByBatchIdIn(batchIds);
        sections.forEach(sec -> sec.getBatch().getId()); // force-load section→batch

        int totalSessions = computeSessionCount(batches, subjects, sections);

        long maxTeacherCapacity = (long) teachers.size() * classTimeslotCount;
        if (totalSessions > maxTeacherCapacity && maxTeacherCapacity > 0) {
            issues.add(warn("CAPACITY",
                    String.format(
                        "Estimated %d sessions but only %d teacher × %d timeslot = %d teacher-slots available. " +
                        "Some sessions may remain unassigned. Consider reducing scope or adding teachers.",
                        totalSessions, teachers.size(), classTimeslotCount, maxTeacherCapacity),
                    null, null));
        }

        // ── 6. Subjects with more required sessions than available timeslots ──
        for (Subject s : subjects) {
            int sessionsPerBatch = s.getWeeklyHours() / s.getChunkHours();
            if (sessionsPerBatch > classTimeslotCount) {
                issues.add(warn("TIMESLOT",
                        String.format("Subject '%s' needs %d sessions per week but only %d timeslots exist. " +
                                "Some sessions will be unscheduled.",
                                label(s), sessionsPerBatch, classTimeslotCount),
                        s.getId(), s.getName()));
            }
        }

        log.info("Feasibility check for req={}: {} errors, {} warnings, ~{} sessions",
                req.name(), issues.stream().filter(i -> i.severity() == FeasibilityIssue.Severity.ERROR).count(),
                issues.stream().filter(i -> i.severity() == FeasibilityIssue.Severity.WARNING).count(),
                totalSessions);

        return result(issues, totalSessions, classTimeslotCount);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private List<Batch> loadBatches(ScheduleRequest req) {
        if (req.batchIds() != null && !req.batchIds().isEmpty())
            return batchRepo.findAllById(req.batchIds());
        if (req.departmentId() != null)
            return batchRepo.findByDepartmentId(req.departmentId());
        return batchRepo.findAll();
    }

    private List<Teacher> loadTeachers(ScheduleRequest req) {
        if (req.teacherIds() != null && !req.teacherIds().isEmpty())
            return teacherRepo.findAllById(req.teacherIds());
        return teacherRepo.findAll();
    }

    private List<Room> loadRooms(ScheduleRequest req) {
        if (req.roomIds() != null && !req.roomIds().isEmpty())
            return roomRepo.findAllById(req.roomIds());
        return roomRepo.findAll();
    }

    private int computeSessionCount(List<Batch> batches, List<Subject> subjects,
                                     List<ClassSection> sections) {
        int total = 0;
        for (Batch b : batches) {
            for (Subject s : subjects) {
                if (!s.getDepartment().getId().equals(b.getDepartment().getId())) continue;
                int perOccurrence = s.getWeeklyHours() / s.getChunkHours();
                if (s.isLab()) {
                    long sectionCount = sections.stream()
                            .filter(sec -> sec.getBatch().getId().equals(b.getId()))
                            .count();
                    total += (int) (perOccurrence * sectionCount);
                } else {
                    total += perOccurrence;
                }
            }
        }
        return total;
    }

    private static String label(Subject s) {
        return s.getName() + (s.getCode() != null ? " (" + s.getCode() + ")" : "");
    }

    private static FeasibilityIssue error(String cat, String msg, Long id, String name) {
        return new FeasibilityIssue(FeasibilityIssue.Severity.ERROR, cat, msg, id, name);
    }

    private static FeasibilityIssue warn(String cat, String msg, Long id, String name) {
        return new FeasibilityIssue(FeasibilityIssue.Severity.WARNING, cat, msg, id, name);
    }

    private static FeasibilityCheckResult result(List<FeasibilityIssue> issues,
                                                  int totalSessions, int timeslots) {
        // Sort: errors first, then warnings
        issues.sort(Comparator.comparing(i -> i.severity() == FeasibilityIssue.Severity.ERROR ? 0 : 1));
        long errors   = issues.stream().filter(i -> i.severity() == FeasibilityIssue.Severity.ERROR).count();
        long warnings = issues.stream().filter(i -> i.severity() == FeasibilityIssue.Severity.WARNING).count();
        return new FeasibilityCheckResult(errors == 0, (int) errors, (int) warnings, totalSessions, timeslots, issues);
    }
}
