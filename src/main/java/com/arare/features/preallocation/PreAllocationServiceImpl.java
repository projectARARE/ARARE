package com.arare.features.preallocation;

import com.arare.common.enums.TimeslotType;
import com.arare.exception.ResourceConflictException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreAllocationServiceImpl implements PreAllocationService {

    private final PreAllocationRepository repo;
    private final ScheduleRepository scheduleRepo;
    private final BatchRepository batchRepo;
    private final SubjectRepository subjectRepo;
    private final TeacherRepository teacherRepo;
    private final RoomRepository roomRepo;
    private final TimeslotRepository timeslotRepo;
    private final ClassSessionRepository sessionRepo;

    @Override
    @Transactional
    public PreAllocationResponse create(PreAllocationRequest req) {
        PreAllocation pa = buildFromSpec(
            scheduleRepo.findById(req.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", req.scheduleId())),
            new PreAllocationSpec(req.batchId(), req.subjectId(), req.teacherId(), req.roomId(), req.timeslotId()),
            req.locked()
        );
        validateWithinSchedule(pa, null);
        return toResponse(repo.save(pa));
    }

    @Override
    @Transactional
    public List<PreAllocationResponse> createAll(Long scheduleId, List<PreAllocationSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));

        // Deterministic insert order so identical wizard payloads produce the
        // same rows (mirrors the solver's REPRODUCIBLE environment mode).
        List<PreAllocationResponse> created = new ArrayList<>();
        for (PreAllocationSpec spec : specs) {
            PreAllocation pa = buildFromSpec(schedule, spec, true);
            PreAllocation saved = repo.save(validateWithinSchedule(pa, null));
            created.add(toResponse(saved));
        }
        return created;
    }

    @Override
    public PreAllocationResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<PreAllocationResponse> findBySchedule(Long scheduleId) {
        return repo.findByScheduleId(scheduleId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    /**
     * Resolves a spec against the persisted catalogs and applies the same
     * validation a solver HARD constraint would: qualified teacher, matching
     * room, CLASS-type slot, availability, and no cross-schedule double-booking.
     */
    private PreAllocation buildFromSpec(Schedule schedule, PreAllocationSpec spec, boolean locked) {
        Batch batch = batchRepo.findById(spec.batchId())
            .orElseThrow(() -> new ResourceNotFoundException("Batch", spec.batchId()));
        Subject subject = subjectRepo.findById(spec.subjectId())
            .orElseThrow(() -> new ResourceNotFoundException("Subject", spec.subjectId()));

        Teacher teacher = null;
        if (spec.teacherId() != null) {
            teacher = teacherRepo.findById(spec.teacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", spec.teacherId()));
            if (!subject.isRequiresTeacher()) {
                throw new IllegalArgumentException(
                    "Subject '" + subject.getName() + "' does not require a teacher; '" + teacher.getName()
                        + "' cannot be pre-assigned to it.");
            }
            if (teacher.getSubjects().stream().noneMatch(ts -> ts.getId().equals(subject.getId()))) {
                throw new IllegalArgumentException(
                    "Teacher '" + teacher.getName() + "' is not qualified to teach subject '" + subject.getName() + "'");
            }
        }

        Room room = null;
        if (spec.roomId() != null) {
            room = roomRepo.findById(spec.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", spec.roomId()));
            if (subject.getRoomTypeRequired() != null && subject.getRoomTypeRequired() != room.getType()) {
                throw new IllegalArgumentException(
                    "Room '" + room.getRoomNumber() + "' (type " + room.getType()
                        + ") does not match subject '" + subject.getName()
                        + "' which requires type " + subject.getRoomTypeRequired());
            }
            if (subject.getLabSubtypeRequired() != null && subject.getLabSubtypeRequired() != room.getLabSubtype()) {
                throw new IllegalArgumentException(
                    "Room '" + room.getRoomNumber() + "' is not " + subject.getLabSubtypeRequired()
                        + " as required by subject '" + subject.getName() + "'");
            }
            if (room.getCapacity() < sectionSizeOf(batch, subject)) {
                throw new IllegalArgumentException(
                    "Room '" + room.getRoomNumber() + "' capacity is " + room.getCapacity()
                        + " but this batch/section needs " + sectionSizeOf(batch, subject) + " seats");
            }
        }

        Timeslot timeslot = null;
        if (spec.timeslotId() != null) {
            timeslot = timeslotRepo.findById(spec.timeslotId())
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", spec.timeslotId()));
            if (timeslot.getType() != TimeslotType.CLASS) {
                throw new IllegalArgumentException(
                    "Session cannot be pre-assigned to a " + timeslot.getType() + " timeslot");
            }
            if (teacher != null && !teacher.getAvailableTimeslots().isEmpty()
                && !teacher.getAvailableTimeslots().contains(timeslot)) {
                throw new IllegalArgumentException(
                    "Teacher '" + teacher.getName() + "' is not available at "
                        + timeslot.getDay() + " " + timeslot.getStartTime() + "-" + timeslot.getEndTime());
            }
            if (room != null && !room.getAvailableTimeslots().isEmpty()
                && !room.getAvailableTimeslots().contains(timeslot)) {
                throw new IllegalArgumentException(
                    "Room '" + room.getRoomNumber() + "' is not available at "
                        + timeslot.getDay() + " " + timeslot.getStartTime() + "-" + timeslot.getEndTime());
            }
            // Reject pre-allocating a teacher to a slot already claimed by an
            // ACTIVE (live) schedule — never queue an infeasible pre-allocation.
            requireTeacherSlotFreeInActiveSchedules(teacher, timeslot, schedule.getId(), schedule.getInstituteId());
        }

        return PreAllocation.builder()
            .schedule(schedule)
            .batch(batch)
            .subject(subject)
            .teacher(teacher)
            .room(room)
            .timeslot(timeslot)
            .locked(locked)
            .build();
    }

    /**
     * Validates this {@code candidate} against the pre-allocations already
     * stored for its schedule. {@code excludedId} lets a create pass skip the
     * candidate's own id (unused on create, present for future update paths).
     * Returns the candidate so callers can chain {@code repo.save}.
     */
    private PreAllocation validateWithinSchedule(PreAllocation candidate, Long excludedId) {
        Long batchId = candidate.getBatch().getId();
        Long subjectId = candidate.getSubject().getId();
        Long teacherId = candidate.getTeacher() != null ? candidate.getTeacher().getId() : null;

        for (PreAllocation other : repo.findByScheduleId(candidate.getSchedule().getId())) {
            if (excludedId != null && excludedId.equals(other.getId())) {
                continue;
            }
            boolean sameSubjectBatch = other.getBatch().getId().equals(batchId)
                && other.getSubject().getId().equals(subjectId);
            if (sameSubjectBatch
                && teacherId != null
                && other.getTeacher() != null
                && !other.getTeacher().getId().equals(teacherId)) {
                throw new ResourceConflictException(
                    "Subject '" + candidate.getSubject().getName() + "' for this batch is already pre-assigned to '"
                        + other.getTeacher().getName() + "'. A subject must be taught by exactly one teacher.");
            }
            // The same teacher pre-assigned to two overlapping slots is
            // infeasible before the solver is even asked to run.
            if (teacherId != null
                && other.getTeacher() != null
                && other.getTeacher().getId().equals(teacherId)
                && overlaps(candidate.getTimeslot(), candidate.getSubject().getChunkHours(),
                            other.getTimeslot(), other.getSubject().getChunkHours())) {
                throw new ResourceConflictException(
                    "Teacher '" + candidate.getTeacher().getName() + "' is already pre-assigned to an "
                        + "overlapping slot for this schedule.");
            }
        }
        return candidate;
    }

    private void requireTeacherSlotFreeInActiveSchedules(Teacher teacher, Timeslot timeslot, Long scheduleId, Long instituteId) {
        if (teacher == null || timeslot == null) {
            return;
        }
        for (ClassSession other : sessionRepo.findActiveCrossScheduleSessions(teacher.getId(), scheduleId, instituteId)) {
            if (other.getTimeslot() != null
                && other.getTimeslot().getDay() == timeslot.getDay()
                && overlaps(timeslot, 1, other.getTimeslot(), other.getDuration())) {
                throw new ResourceConflictException(
                    "Teacher '" + teacher.getName() + "' is already scheduled in another active timetable at "
                        + timeslot.getDay() + " " + timeslot.getStartTime() + "-" + timeslot.getEndTime()
                        + ". Move that session or pick a different slot.");
            }
        }
    }

    private static int sectionSizeOf(Batch batch, Subject subject) {
        return batch.getStudentCount();
    }

    private static boolean overlaps(Timeslot a, int aDuration, Timeslot b, int bDuration) {
        if (a == null || b == null) {
            return false;
        }
        Integer aStartSlot = a.getSlotNumber();
        Integer bStartSlot = b.getSlotNumber();
        if (aStartSlot != null && bStartSlot != null) {
            int aEndExclusive = aStartSlot + aDuration;
            int bEndExclusive = bStartSlot + bDuration;
            return aStartSlot < bEndExclusive && bStartSlot < aEndExclusive;
        }
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }

    private PreAllocation findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("PreAllocation", id));
    }

    private PreAllocationResponse toResponse(PreAllocation pa) {
        var batch = pa.getBatch();
        String batchLabel = batch.getDepartment().getName() + "-" + batch.getYear() + batch.getSection();
        return new PreAllocationResponse(
            pa.getId(), pa.getSchedule().getId(),
            batch.getId(), batchLabel,
            pa.getSubject().getId(), pa.getSubject().getName(),
            pa.getTeacher() != null ? pa.getTeacher().getId() : null,
            pa.getTeacher() != null ? pa.getTeacher().getName() : null,
            pa.getRoom() != null ? pa.getRoom().getId() : null,
            pa.getRoom() != null ? pa.getRoom().getRoomNumber() : null,
            pa.getTimeslot() != null ? pa.getTimeslot().getId() : null,
            pa.getTimeslot() != null ? pa.getTimeslot().getDay().toString() : null,
            pa.getTimeslot() != null ? pa.getTimeslot().getStartTime().toString() : null,
            pa.isLocked()
        );
    }
}