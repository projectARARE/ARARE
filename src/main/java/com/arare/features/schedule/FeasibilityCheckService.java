package com.arare.features.schedule;

import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.room.Room;
import com.arare.features.preallocation.PreAllocationSpec;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.subjectoffering.SubjectOffering;
import com.arare.features.subjectoffering.SubjectOfferingRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.teacherassignment.TeacherAssignment;
import com.arare.features.teacherassignment.TeacherAssignmentRepository;
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
    private final ClassSessionRepository sessionRepo;
    private final TeacherAssignmentRepository assignmentRepo;
    private final SubjectOfferingRepository offeringRepo;

    @Transactional(readOnly = true)
    public FeasibilityCheckResult check(ScheduleRequest req) {
        List<FeasibilityIssue> issues = new ArrayList<>();

        //  1. Load entities scoped to the request 
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

        List<Subject> scoped = req.departmentId() != null
                ? subjectRepo.findByDepartmentId(req.departmentId())
                : req.instituteId() != null
                        ? subjectRepo.findByDepartmentInstituteId(req.instituteId())
                        : subjectRepo.findAll().stream()
                                .filter(s -> s.getDepartment() == null
                                        || deptIds.contains(s.getDepartment().getId()))
                                .toList();
        // Institute-wide subjects (no owning department) are eligible in every
        // scope: they can be offered to any batch via SubjectOffering.
        List<Subject> subjects = new ArrayList<>(scoped);
        for (Subject instWide : subjectRepo.findInstituteWide()) {
            if (subjects.stream().noneMatch(s -> s.getId().equals(instWide.getId()))) {
                subjects.add(instWide);
            }
        }

        if (subjects.isEmpty()) {
            issues.add(warn("SUBJECT",
                    "No subjects found for the selected batches' departments. Add subjects to enable scheduling.",
                    null, null));
        }

        //  2. No CLASS timeslots 
        if (classTimeslotCount == 0) {
            issues.add(error("TIMESLOT",
                    "No CLASS-type timeslots are configured. Add timeslots before generating a schedule.",
                    null, null));
            return result(issues, 0, 0);
        }

        //  3. Subject → teacher qualification check (ERROR if none) 
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

        //  3b. Multi-slot subjects need deterministic slot ordering 
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

        //  4. Lab subject → room type check (ERROR if no room of required type)
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

        //  5. Estimate total sessions & global capacity 
        List<Long> batchIds = batches.stream().map(Batch::getId).toList();
        List<ClassSection> sections = batchIds.isEmpty()
                ? Collections.emptyList()
                : sectionRepo.findByBatchIdIn(batchIds);
        sections.forEach(sec -> sec.getBatch().getId()); // force-load section→batch
        batches.forEach(b -> b.getSubjects().size());    // force-load batch curriculum
        sections.forEach(sec -> sec.getSubjects().size()); // force-load section curriculum

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

        //  6. Subjects with more required sessions than available timeslots 
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

    // 7. Pre-assignments from the wizard must be individually schedulable
    // (qualified teacher, usable room, CLASS slot, no cross-schedule clash),
    // otherwise the solver would be asked to run on an infeasible request.
    checkPreAllocations(req, issues, teachers, rooms);

    //  8. Term teacher allotments: the solver HARD-rejects any non-allotted
    //  teacher, so an allotment must resolve to exactly one teacher who is
    //  actually inside the schedule's teacher scope.
    checkTeacherAllotments(issues, batches, sections, subjects, teachers);

        log.info("Feasibility check for req={}: {} errors, {} warnings, ~{} sessions",
                req.name(), issues.stream().filter(i -> i.severity() == FeasibilityIssue.Severity.ERROR).count(),
                issues.stream().filter(i -> i.severity() == FeasibilityIssue.Severity.WARNING).count(),
                totalSessions);

        return result(issues, totalSessions, classTimeslotCount);
    }


    private void checkPreAllocations(ScheduleRequest req, List<FeasibilityIssue> issues,
                                     List<Teacher> requestTeachers, List<Room> requestRooms) {
        if (req.preAllocations() == null || req.preAllocations().isEmpty()) {
            return;
        }
        Map<Long, Teacher> teacherById = requestTeachers.stream().collect(Collectors.toMap(Teacher::getId, t -> t));
        Map<Long, Room> roomById = requestRooms.stream().collect(Collectors.toMap(Room::getId, r -> r));
        Map<Long, Batch> batchById = loadBatches(req).stream().collect(Collectors.toMap(Batch::getId, b -> b));
        Map<Long, Subject> subjectById = subjectRepo.findAll().stream().collect(Collectors.toMap(Subject::getId, s -> s));
        List<Timeslot> classSlots = timeslotRepo.findByType(TimeslotType.CLASS);
        Map<Long, Timeslot> slotById = classSlots.stream().collect(Collectors.toMap(Timeslot::getId, t -> t));

        List<PreAllocationSpec> specs = req.preAllocations();
        for (int i = 0; i < specs.size(); i++) {
            PreAllocationSpec spec = specs.get(i);
            Batch batch = batchById.get(spec.batchId());
            Subject subject = subjectById.get(spec.subjectId());
            if (batch == null || subject == null) {
                issues.add(error("PRE-ALLOCATION",
                    "Pre-allocation #" + (i + 1) + " references a missing batch (" + spec.batchId()
                        + ") or subject (" + spec.subjectId() + ").",
                    spec.batchId(), null));
                continue;
            }
            Teacher teacher = spec.teacherId() != null ? teacherById.get(spec.teacherId()) : null;
            if (spec.teacherId() != null && teacher == null) {
                issues.add(error("PRE-ALLOCATION",
                    "Pre-assigned teacher " + spec.teacherId() + " is outside the schedule's teacher scope.",
                    spec.teacherId(), null));
                continue;
            }
            if (teacher != null
                && !teacher.getSubjects().stream().anyMatch(ts -> ts.getId().equals(subject.getId()))) {
                issues.add(error("PRE-ALLOCATION",
                    "Teacher '" + teacher.getName() + "' is not qualified to teach '" + subject.getName() + "'.",
                    spec.teacherId(), teacher.getName()));
            }
            Room room = spec.roomId() != null ? roomById.get(spec.roomId()) : null;
            if (spec.roomId() != null && room == null) {
                issues.add(error("PRE-ALLOCATION",
                    "Pre-assigned room " + spec.roomId() + " is outside the schedule's room scope.",
                    spec.roomId(), null));
            } else if (room != null && subject.getRoomTypeRequired() != null
                && subject.getRoomTypeRequired() != room.getType()) {
                issues.add(error("PRE-ALLOCATION",
                    "Pre-assigned room '" + room.getRoomNumber() + "' (" + room.getType()
                        + ") does not match subject '" + subject.getName() + "' which requires "
                        + subject.getRoomTypeRequired(),
                    spec.roomId(), room.getRoomNumber()));
            }

            Timeslot slot = spec.timeslotId() != null ? slotById.get(spec.timeslotId()) : null;
            if (spec.timeslotId() != null && slot == null) {
                issues.add(error("PRE-ALLOCATION",
                    "Pre-allocation timeslot " + spec.timeslotId() + " is not a CLASS slot.",
                    spec.timeslotId(), null));
                continue;
            }
            int duration = subject.getChunkHours();

            // Exactly one teacher per (subject, effectiveBatch) -- mirrors the
            // singleTeacherPerSubjectSection HARD constraint.
            for (int j = 0; j < specs.size(); j++) {
                if (i == j) {
                    continue;
                }
                PreAllocationSpec other = specs.get(j);
                if (Objects.equals(other.batchId(), spec.batchId())
                    && Objects.equals(other.subjectId(), spec.subjectId())
                    && spec.teacherId() != null && other.teacherId() != null
                    && !spec.teacherId().equals(other.teacherId())) {
                    issues.add(error("PRE-ALLOCATION",
                        "Subject '" + subject.getName() + "' for batch "
                            + batchLabel(batch) + " is pre-assigned to two different teachers.",
                        spec.batchId(), null));
                }
            }

            if (slot == null || teacher == null) {
                continue;
            }
            // Two pre-allocations of the same teacher cannot overlap.
            for (int j = i + 1; j < specs.size(); j++) {
                PreAllocationSpec other = specs.get(j);
                if (!Objects.equals(other.teacherId(), spec.teacherId()) || other.timeslotId() == null) {
                    continue;
                }
                Subject otherSubject = subjectById.get(other.subjectId());
                Timeslot otherSlot = slotById.get(other.timeslotId());
                if (otherSubject == null || otherSlot == null) {
                    continue;
                }
                if (otherSlot.getDay() == slot.getDay()
                    && overlaps(slot, duration, otherSlot, otherSubject.getChunkHours())) {
                    issues.add(error("PRE-ALLOCATION",
                        "Teacher '" + teacher.getName() + "' is pre-assigned to overlapping slots.",
                        spec.teacherId(), teacher.getName()));
                }
            }
            // Cross-schedule gate: the teacher must not be booked in another
            // ACTIVE timetable at the pre-assigned slot.
            for (ClassSession busy : sessionRepo.findActiveCrossScheduleSessions(teacher.getId(), -1L, req.instituteId())) {
                if (busy.getTimeslot() != null
                    && busy.getTimeslot().getDay() == slot.getDay()
                    && overlaps(slot, duration, busy.getTimeslot(), busy.getDuration())) {
                    issues.add(error("PRE-ALLOCATION",
                        "Teacher '" + teacher.getName()
                            + "' is already teaching in another active timetable at "
                            + slot.getDay() + " " + slot.getStartTime() + "-" + slot.getEndTime() + ".",
                        spec.teacherId(), teacher.getName()));
                }
            }
        }
    }

    private void checkTeacherAllotments(List<FeasibilityIssue> issues,
                                        List<Batch> batches,
                                        List<ClassSection> sections,
                                        List<Subject> subjects,
                                        List<Teacher> requestTeachers) {
        if (batches.isEmpty()) {
            return;
        }
        List<Long> batchIds = batches.stream().map(Batch::getId).toList();
        List<Long> sectionIds = sections.stream().map(ClassSection::getId).toList();

        List<TeacherAssignment> assignments = new ArrayList<>();
        if (!batchIds.isEmpty()) {
            assignments.addAll(assignmentRepo.findByBatchIdIn(batchIds));
        }
        if (!sectionIds.isEmpty()) {
            assignments.addAll(assignmentRepo.findBySectionIdIn(sectionIds));
        }
        if (assignments.isEmpty()) {
            return;
        }
        assignments.forEach(a -> {
            a.getSubject().getId();
            a.getTeacher().getId();
            if (a.getBatch() != null) a.getBatch().getId();
            if (a.getSection() != null) a.getSection().getId();
        });

        Map<Long, Subject> subjectById = subjects.stream()
                .collect(Collectors.toMap(Subject::getId, s -> s));
        Map<Long, Teacher> teacherById = requestTeachers.stream()
                .collect(Collectors.toMap(Teacher::getId, t -> t));
        Set<Long> requestedTeacherIds = teacherById.keySet();

        // Group by (batch, subject): the singleTeacherPerSubjectSection HARD
        // constraint groups every session of a subject under one effectiveBatch,
        // so all section- and batch-level allotments for a batch+subject must
        // resolve to exactly one teacher.
        Map<Batch, Map<Long, List<TeacherAssignment>>> byBatchSubject = new HashMap<>();
        for (TeacherAssignment a : assignments) {
            Batch b = a.getBatch();
            if (b == null && a.getSection() != null) {
                b = a.getSection().getBatch();
            }
            if (b == null) {
                continue;
            }
            byBatchSubject
                .computeIfAbsent(b, k -> new HashMap<>())
                .computeIfAbsent(a.getSubject().getId(), k -> new ArrayList<>())
                .add(a);
        }

        for (Map.Entry<Batch, Map<Long, List<TeacherAssignment>>> batchEntry : byBatchSubject.entrySet()) {
            Batch batch = batchEntry.getKey();
            for (Map.Entry<Long, List<TeacherAssignment>> subjectEntry : batchEntry.getValue().entrySet()) {
                List<TeacherAssignment> subjectAssignments = subjectEntry.getValue();
                List<Long> allottedIds = subjectAssignments.stream()
                        .map(a -> a.getTeacher().getId())
                        .distinct()
                        .toList();
                Subject subject = subjectById.get(subjectEntry.getKey());
                String subjectLabel = subject != null ? label(subject) : "Subject #" + subjectEntry.getKey();

                if (allottedIds.size() > 1) {
                    String names = allottedIds.stream()
                            .map(id -> teacherById.get(id))
                            .filter(Objects::nonNull)
                            .map(Teacher::getName)
                            .collect(Collectors.joining(", "));
                    issues.add(error("TEACHER",
                            "Subject '" + subjectLabel + "' in batch " + batchLabel(batch)
                                + " is allotted to multiple teachers (" + names
                                + "). The one-teacher-per-class rule would make this infeasible.",
                            subjectEntry.getKey(), subjectLabel));
                    continue;
                }

                Long allottedId = allottedIds.get(0);
                if (!requestedTeacherIds.contains(allottedId)) {
                    Teacher allotted = teacherById.get(allottedId);
                    String name = allotted != null ? allotted.getName() : "Teacher #" + allottedId;
                    issues.add(error("TEACHER",
                            "Allotted teacher '" + name + "' for '" + subjectLabel + "' in batch "
                                + batchLabel(batch) + " is outside the schedule's teacher scope. "
                                + "Add the teacher to the scope or change the allotment.",
                            allottedId, name));
                }
            }
        }
    }

    private static boolean overlaps(Timeslot a, int aDuration, Timeslot b, int bDuration) {
        Integer aStartSlot = a.getSlotNumber();
        Integer bStartSlot = b.getSlotNumber();
        if (aStartSlot != null && bStartSlot != null) {
            int aEndExclusive = aStartSlot + aDuration;
            int bEndExclusive = bStartSlot + bDuration;
            return aStartSlot < bEndExclusive && bStartSlot < aEndExclusive;
        }
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }
    private List<Batch> loadBatches(ScheduleRequest req) {
        if (req.batchIds() != null && !req.batchIds().isEmpty())
            return batchRepo.findAllById(req.batchIds());
        if (req.departmentId() != null)
            return batchRepo.findByDepartmentId(req.departmentId());
        if (req.instituteId() != null)
            return batchRepo.findByDepartmentInstituteId(req.instituteId());
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
        // Preload offerings for this batch and its sections (mirrors
        // StandardSessionGenerator: an explicit offering list wins over the
        // legacy join-table curriculum).
        Map<Long, List<SubjectOffering>> batchOfferings = indexOfferings(
            offeringRepo.findByBatchId(batch.getId()));
        Map<Long, List<SubjectOffering>> sectionOfferings = indexOfferings(
            sectionRepo.findByBatchId(batch.getId()).isEmpty()
                ? List.of()
                : offeringRepo.findBySectionIdIn(
                    sectionRepo.findByBatchId(batch.getId()).stream()
                        .map(ClassSection::getId).toList()));

        for (Subject s : subjects) {
            // Institute-wide subjects have no owning department.
            if (s.getDepartment() != null
                && !s.getDepartment().getId().equals(batch.getDepartment().getId())) continue;
            // Curriculum scoping mirrors StandardSessionGenerator: a batch with
            // its own curriculum only schedules the subjects it offers.
            if (!batchTakesSubject(batch, s, batchOfferings)) continue;
            int weeklyHours = effectiveWeeklyHours(batch, s, batchOfferings);
            int perOccurrence = weeklyHours / s.getChunkHours();
            if (s.isLab()) {
                long sectionCount = sections.stream()
                    .filter(sec -> sec.getBatch().getId().equals(batch.getId()))
                    .filter(sec -> sectionTakesSubject(sec, s, sectionOfferings))
                    .count();
                if (sectionCount == 0) {
                    // No section offers the lab split: the generator falls back
                    // to whole-batch lab sessions.
                    total += perOccurrence;
                } else {
                    total += (int) (perOccurrence * sectionCount);
                }
            } else {
                total += perOccurrence;
            }
        }
        return total;
    }

    private Map<Long, List<SubjectOffering>> indexOfferings(List<SubjectOffering> offerings) {
        return offerings.stream().collect(Collectors.groupingBy(o -> {
            if (o.getBatch() != null) {
                return o.getBatch().getId();
            }
            return o.getSection().getId();
        }));
    }

    // Curriculum fallback chain: explicit SubjectOffering list -> section
    // curriculum -> batch curriculum -> department-offered (all subjects).
    // Empty curriculum means "inherit".
    private boolean batchTakesSubject(Batch batch, Subject subject,
                                      Map<Long, List<SubjectOffering>> batchOfferings) {
        List<SubjectOffering> offerings = batchOfferings.get(batch.getId());
        if (offerings != null && !offerings.isEmpty()) {
            return offerings.stream().anyMatch(o -> o.getSubject().getId().equals(subject.getId()));
        }
        List<Subject> curriculum = batch.getSubjects();
        return curriculum == null || curriculum.isEmpty()
            || curriculum.stream().anyMatch(s -> s.getId().equals(subject.getId()));
    }

    private boolean sectionTakesSubject(ClassSection section, Subject subject,
                                        Map<Long, List<SubjectOffering>> sectionOfferings) {
        List<SubjectOffering> offerings = sectionOfferings.get(section.getId());
        if (offerings != null && !offerings.isEmpty()) {
            return offerings.stream().anyMatch(o -> o.getSubject().getId().equals(subject.getId()));
        }
        List<Subject> curriculum = section.getSubjects();
        return curriculum == null || curriculum.isEmpty()
            || curriculum.stream().anyMatch(s -> s.getId().equals(subject.getId()));
    }

    private int effectiveWeeklyHours(Batch batch, Subject subject,
                                     Map<Long, List<SubjectOffering>> batchOfferings) {
        List<SubjectOffering> offerings = batchOfferings.get(batch.getId());
        if (offerings != null && !offerings.isEmpty()) {
            for (SubjectOffering o : offerings) {
                if (o.getSubject().getId().equals(subject.getId()) && o.getWeeklyHours() != null) {
                    return o.getWeeklyHours();
                }
            }
        }
        return subject.getWeeklyHours();
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
