package com.arare.features.classsession;

import com.arare.common.enums.TimeslotType;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassSessionServiceImpl implements ClassSessionService {

    private final ClassSessionRepository repo;
    private final TeacherRepository teacherRepo;
    private final RoomRepository roomRepo;
    private final TimeslotRepository timeslotRepo;
    private final ScheduleRepository scheduleRepo;
    private final SubjectRepository subjectRepo;
    private final BatchRepository batchRepo;
    private final ClassSectionRepository sectionRepo;

    @Override
    public List<ClassSessionResponse> findBySchedule(Long scheduleId) {
        return repo.findByScheduleId(scheduleId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<ClassSessionResponse> findByScheduleAndBatch(Long scheduleId, Long batchId) {
        return repo.findByScheduleIdAndBatchId(scheduleId, batchId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<ClassSessionResponse> findByScheduleAndTeacher(Long scheduleId, Long teacherId) {
        return repo.findByScheduleIdAndTeacherId(scheduleId, teacherId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ClassSessionResponse updateAssignment(Long sessionId, SessionAssignmentRequest req) {
        ClassSession s = repo.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ClassSession", sessionId));

        boolean assignmentChange =
            req.teacherId() != null || Boolean.TRUE.equals(req.clearTeacher())
                || req.roomId() != null || Boolean.TRUE.equals(req.clearRoom())
                || req.timeslotId() != null || Boolean.TRUE.equals(req.clearTimeslot());
        boolean unlocking = Boolean.FALSE.equals(req.locked());
        if (s.isLocked() && assignmentChange && !unlocking) {
            throw new IllegalArgumentException(
                "Session " + sessionId + " is locked — unlock it before editing its assignment");
        }

        // Resolve the post-edit assignment first (without saving), then reject
        // any change that violates a solver HARD constraint. Manual edits must
        // never introduce states the solver would reject.
        Teacher   newTeacher   = s.getTeacher();
        Room      newRoom      = s.getRoom();
        Timeslot  newTimeslot  = s.getTimeslot();

        if (req.teacherId() != null) {
            Teacher t = teacherRepo.findById(req.teacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", req.teacherId()));
            requireTeacherValid(s, t);
            newTeacher = t;
        } else if (Boolean.TRUE.equals(req.clearTeacher())) {
            if (s.getSubject().isRequiresTeacher()) {
                throw new IllegalArgumentException(
                    "Subject '" + s.getSubject().getName() + "' requires a teacher; unassigning is not allowed");
            }
            newTeacher = null;
        }

        if (req.roomId() != null) {
            Room r = roomRepo.findById(req.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", req.roomId()));
            requireRoomValid(s, r);
            newRoom = r;
        } else if (Boolean.TRUE.equals(req.clearRoom())) {
            if (s.getSubject().isRequiresRoom()) {
                throw new IllegalArgumentException(
                    "Subject '" + s.getSubject().getName() + "' requires a room; unassigning is not allowed");
            }
            newRoom = null;
        }

        if (req.timeslotId() != null) {
            Timeslot ts = timeslotRepo.findById(req.timeslotId())
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", req.timeslotId()));
            if (ts.getType() != TimeslotType.CLASS) {
                throw new IllegalArgumentException(
                    "Session cannot be assigned to a " + ts.getType() + " timeslot");
            }
            newTimeslot = ts;
        } else if (Boolean.TRUE.equals(req.clearTimeslot())) {
            newTimeslot = null;
        }

        requireAvailability(newTeacher, newRoom, newTimeslot);
        requireNoHardConflicts(s, newTeacher, newRoom, newTimeslot);

        s.setTeacher(newTeacher);
        s.setRoom(newRoom);
        s.setTimeslot(newTimeslot);

        if (req.locked() != null) {
            s.setLocked(req.locked());
        }

        return toResponse(repo.save(s));
    }

    @Override
    @Transactional
    public ClassSessionResponse create(SessionCreateRequest req) {
        Schedule schedule = scheduleRepo.findById(req.scheduleId())
            .orElseThrow(() -> new ResourceNotFoundException("Schedule", req.scheduleId()));
        Subject subject = subjectRepo.findById(req.subjectId())
            .orElseThrow(() -> new ResourceNotFoundException("Subject", req.subjectId()));

        if (req.batchId() == null && req.sectionId() == null) {
            throw new IllegalArgumentException("A session must reference either a batch or a section");
        }
        if (req.batchId() != null && req.sectionId() != null) {
            throw new IllegalArgumentException("A session cannot reference both a batch and a section");
        }

        ClassSession.ClassSessionBuilder builder = ClassSession.builder()
            .schedule(schedule)
            .subject(subject)
            .duration(req.duration() != null ? req.duration() : subject.getChunkHours())
            .isLocked(Boolean.TRUE.equals(req.locked()));
        if (req.batchId() != null) {
            builder.batch(batchRepo.findById(req.batchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch", req.batchId())));
        }
        if (req.sectionId() != null) {
            builder.section(sectionRepo.findById(req.sectionId())
                .orElseThrow(() -> new ResourceNotFoundException("ClassSection", req.sectionId())));
        }
        ClassSession s = builder.build();

        if (req.teacherId() != null) {
            Teacher t = teacherRepo.findById(req.teacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", req.teacherId()));
            requireTeacherValid(s, t);
            s.setTeacher(t);
        }
        if (req.roomId() != null) {
            Room r = roomRepo.findById(req.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", req.roomId()));
            requireRoomValid(s, r);
            s.setRoom(r);
        }
        if (req.timeslotId() != null) {
            Timeslot ts = timeslotRepo.findById(req.timeslotId())
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", req.timeslotId()));
            if (ts.getType() != TimeslotType.CLASS) {
                throw new IllegalArgumentException(
                    "Session cannot be assigned to a " + ts.getType() + " timeslot");
            }
            s.setTimeslot(ts);
        }

        requireAvailability(s.getTeacher(), s.getRoom(), s.getTimeslot());
        requireNoHardConflicts(s, s.getTeacher(), s.getRoom(), s.getTimeslot());

        return toResponse(repo.save(s));
    }

    private void requireTeacherValid(ClassSession s, Teacher t) {
        Subject subject = s.getSubject();
        if (!subject.isRequiresTeacher()) {
            throw new IllegalArgumentException(
                "Subject '" + subject.getName() + "' does not require a teacher; a teacher cannot be assigned");
        }
        if (t.getSubjects().stream().noneMatch(ts -> ts.getId().equals(subject.getId()))) {
            throw new IllegalArgumentException(
                "Teacher '" + t.getName() + "' is not qualified to teach subject '" + subject.getName() + "'");
        }
    }

    private void requireRoomValid(ClassSession s, Room r) {
        Subject subject = s.getSubject();
        if (subject.getRoomTypeRequired() != null && subject.getRoomTypeRequired() != r.getType()) {
            throw new IllegalArgumentException(
                "Room '" + r.getRoomNumber() + "' (type " + r.getType()
                    + ") does not match subject '" + subject.getName()
                    + "' which requires type " + subject.getRoomTypeRequired());
        }
        if (subject.getLabSubtypeRequired() != null
            && subject.getLabSubtypeRequired() != r.getLabSubtype()) {
            throw new IllegalArgumentException(
                "Room '" + r.getRoomNumber() + "' is not a " + subject.getLabSubtypeRequired()
                    + " as required by subject '" + subject.getName() + "'");
        }
        if (r.getCapacity() < s.getEffectiveStudentCount()) {
            throw new IllegalArgumentException(
                "Room '" + r.getRoomNumber() + "' capacity is " + r.getCapacity()
                    + " but the session needs " + s.getEffectiveStudentCount() + " seats");
        }
    }

    private void requireAvailability(Teacher teacher, Room room, Timeslot timeslot) {
        if (timeslot == null) {
            return;
        }
        if (teacher != null
            && !teacher.getAvailableTimeslots().isEmpty()
            && !teacher.getAvailableTimeslots().contains(timeslot)) {
            throw new IllegalArgumentException(
                "Teacher '" + teacher.getName() + "' is not available at "
                    + timeslot.getDay() + " " + timeslot.getStartTime() + "-" + timeslot.getEndTime());
        }
        if (room != null
            && !room.getAvailableTimeslots().isEmpty()
            && !room.getAvailableTimeslots().contains(timeslot)) {
            throw new IllegalArgumentException(
                "Room '" + room.getRoomNumber() + "' is not available at "
                    + timeslot.getDay() + " " + timeslot.getStartTime() + "-" + timeslot.getEndTime());
        }
    }

    private void requireNoHardConflicts(ClassSession s, Teacher teacher, Room room, Timeslot timeslot) {
        if (timeslot == null) {
            return;
        }
        List<ClassSession> others = repo.findByScheduleId(s.getSchedule().getId()).stream()
            .filter(o -> !o.getId().equals(s.getId()))
            .filter(o -> o.getTimeslot() != null)
            .filter(o -> o.getTimeslot().getDay() == timeslot.getDay())
            .filter(o -> overlaps(timeslot, s.getDuration(), o.getTimeslot(), o.getDuration()))
            .toList();

        for (ClassSession other : others) {
            if (teacher != null && teacher.getId().equals(other.getTeacher() != null ? other.getTeacher().getId() : null)) {
                throw new IllegalArgumentException(
                    "Teacher '" + teacher.getName() + "' is already teaching another session at that time");
            }
            if (room != null && room.getId().equals(other.getRoom() != null ? other.getRoom().getId() : null)) {
                throw new IllegalArgumentException(
                    "Room '" + room.getRoomNumber() + "' is already occupied by another session at that time");
            }
            if (s.getSection() != null && other.getSection() != null
                && s.getSection().getId().equals(other.getSection().getId())) {
                throw new IllegalArgumentException(
                    "Section already has a session at this timeslot");
            }
            if (s.getEffectiveBatch() != null && other.getEffectiveBatch() != null
                && s.getEffectiveBatch().getId().equals(other.getEffectiveBatch().getId())) {
                throw new IllegalArgumentException(
                    "Batch already has a session at this timeslot");
            }
        }
    }

    /**
     * Same overlap semantics as the solver's {@code overlapsByPlannedDuration}:
     * slot-number based when available, wall-clock fallback otherwise. The
     * session duration is taken from each side's timeslot-sized unit count.
     */
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

    private ClassSessionResponse toResponse(ClassSession s) {
        String batchLabel = s.getSection() != null
            ? s.getSection().getBatch().getDepartment().getName()
              + "-" + s.getSection().getBatch().getYear()
              + s.getSection().getBatch().getSection()
              + " [" + s.getSection().getLabel() + "]"
            : (s.getBatch() != null
                ? s.getBatch().getDepartment().getName()
                  + "-" + s.getBatch().getYear()
                  + s.getBatch().getSection()
                : "N/A");

        return new ClassSessionResponse(
            s.getId(),
            s.getSubject().getId(),
            s.getSubject().getName(),
            s.getSubject().isLab(),
            s.getBatch() != null
                ? s.getBatch().getId()
                : (s.getSection() != null ? s.getSection().getBatch().getId() : null),
            s.getSection() != null ? s.getSection().getId() : null,
            batchLabel,
            s.getTeacher() != null ? s.getTeacher().getId() : null,
            s.getTeacher() != null ? s.getTeacher().getName() : null,
            s.getRoom() != null ? s.getRoom().getId() : null,
            s.getRoom() != null ? s.getRoom().getRoomNumber() : null,
            s.getRoom() != null ? s.getRoom().getBuilding().getName() : null,
            s.getTimeslot() != null ? s.getTimeslot().getId() : null,
            s.getTimeslot() != null ? s.getTimeslot().getDay().toString() : null,
            s.getTimeslot() != null ? s.getTimeslot().getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null,
            s.getTimeslot() != null ? s.getTimeslot().getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null,
            s.getDuration(),
            s.isLocked()
        );
    }
}
