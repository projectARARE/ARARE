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
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

// Pre-solve feasibility validator — the Constraint Propagation layer.
// <p>Runs lightweight checks before the Timefold solver is invoked to:
// <ol>
// <li>Detect hard errors that guarantee solver infeasibility (e.g. a subject
// with no qualified teacher).</li>
// <li>Surface warnings that typically produce a poor score (e.g. more sessions
// than available teacher-timeslot slots).</li>
// </ol>
// <p>This is O(batches × subjects) — fast enough to run interactively
// in the UI before clicking "Generate Schedule".</p>
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
        List<Timeslot> classTimeslots = timeslotRepo.findByType(TimeslotType.CLASS);
        int classTimeslotCount = classTimeslots.size();

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
            if (s.getChunkHours() <= 0) {
            issues.add(error("SUBJECT",
                "Subject has invalid chunkHours (must be > 0): " + label(s),
                s.getId(), s.getName()));
            continue;
            }
            if (s.getWeeklyHours() % s.getChunkHours() != 0) {
            issues.add(error("SUBJECT",
                String.format("Subject '%s' has weeklyHours=%d not divisible by chunkHours=%d. " +
                        "This causes silent under-scheduling.",
                    label(s), s.getWeeklyHours(), s.getChunkHours()),
                s.getId(), s.getName()));
            }
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

        // ── 3b. Multi-slot subjects need deterministic slot ordering ─────────
        int maxChunkUnits = subjects.stream().mapToInt(Subject::getChunkHours).max().orElse(1);
        if (maxChunkUnits > 1) {
            boolean hasSlotNumbers = classTimeslots.stream().anyMatch(t -> t.getSlotNumber() != null);
            if (!hasSlotNumbers) {
                issues.add(error("TIMESLOT",
                    "At least one subject requires multi-slot sessions, but CLASS timeslots have no slotNumber ordering. "
                        + "Provide slot numbers to enable contiguous slot scheduling.",
                    null, null));
            }

            int maxConsecutive = longestConsecutiveClassRun(classTimeslots);
            if (maxConsecutive < maxChunkUnits) {
                issues.add(error("TIMESLOT",
                    "Largest contiguous CLASS slot run is " + maxConsecutive
                        + ", but a subject requires chunk size " + maxChunkUnits
                        + ". Add contiguous slots or reduce chunk size.",
                    null, null));
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

        // A single batch cannot occupy more sessions than available class slots.
        for (Batch batch : batches) {
            int batchSessions = computeSessionCountForBatch(batch, subjects, sections);
            if (batchSessions > classTimeslotCount) {
                issues.add(error("CAPACITY",
                    String.format(
                        "Batch %s requires %d sessions, but only %d CLASS timeslots exist in the week. "
                            + "This is infeasible for that batch.",
                        batchLabel(batch), batchSessions, classTimeslotCount),
                    batch.getId(), batchLabel(batch)));
            }
        }

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
                issues.add(error("TIMESLOT",
                        String.format("Subject '%s' needs %d sessions per week but only %d timeslots exist. " +
                                "This is infeasible.",
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
            total += computeSessionCountForBatch(b, subjects, sections);
        }
        return total;
    }

    private int computeSessionCountForBatch(Batch batch, List<Subject> subjects,
                                            List<ClassSection> sections) {
        int total = 0;
        for (Subject s : subjects) {
            if (!s.getDepartment().getId().equals(batch.getDepartment().getId())) continue;
            int perOccurrence = s.getWeeklyHours() / s.getChunkHours();
            if (s.isLab()) {
                long sectionCount = sections.stream()
                    .filter(sec -> sec.getBatch().getId().equals(batch.getId()))
                    .count();
                total += (int) (perOccurrence * sectionCount);
            } else {
                total += perOccurrence;
            }
        }
        return total;
    }

    private int longestConsecutiveClassRun(List<Timeslot> classTimeslots) {
        Map<Object, List<Timeslot>> byDay = classTimeslots.stream()
            .collect(Collectors.groupingBy(Timeslot::getDay));

        int best = 0;
        for (List<Timeslot> daySlots : byDay.values()) {
            List<Timeslot> ordered = daySlots.stream()
                .sorted((a, b) -> {
                    if (a.getSlotNumber() != null && b.getSlotNumber() != null) {
                        return Integer.compare(a.getSlotNumber(), b.getSlotNumber());
                    }
                    if (a.getSlotNumber() != null) return -1;
                    if (b.getSlotNumber() != null) return 1;
                    return a.getStartTime().compareTo(b.getStartTime());
                })
                .toList();

            int run = 0;
            Timeslot prev = null;
            for (Timeslot cur : ordered) {
                if (prev == null) {
                    run = 1;
                } else if (areConsecutive(prev, cur)) {
                    run += 1;
                } else {
                    run = 1;
                }
                best = Math.max(best, run);
                prev = cur;
            }
        }
        return best;
    }

    private boolean areConsecutive(Timeslot a, Timeslot b) {
        if (a.getSlotNumber() != null && b.getSlotNumber() != null) {
            return b.getSlotNumber() == a.getSlotNumber() + 1;
        }
        return a.getEndTime().equals(b.getStartTime());
    }

    private String batchLabel(Batch b) {
        return b.getDepartment().getCode() + "-Y" + b.getYear() + b.getSection();
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
