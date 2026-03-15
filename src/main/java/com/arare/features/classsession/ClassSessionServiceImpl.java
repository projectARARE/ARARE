package com.arare.features.classsession;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassSessionServiceImpl implements ClassSessionService {

    private final ClassSessionRepository repo;
    private final TeacherRepository teacherRepo;
    private final RoomRepository roomRepo;
    private final TimeslotRepository timeslotRepo;

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

        if (req.teacherId() != null) {
            Teacher t = teacherRepo.findById(req.teacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", req.teacherId()));
            s.setTeacher(t);
        } else if (req.teacherId() == null && req.locked() == null) {
            // explicit null means "keep current"
        }

        if (req.roomId() != null) {
            Room r = roomRepo.findById(req.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", req.roomId()));
            s.setRoom(r);
        }

        if (req.timeslotId() != null) {
            Timeslot ts = timeslotRepo.findById(req.timeslotId())
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", req.timeslotId()));
            s.setTimeslot(ts);
        }

        if (req.locked() != null) {
            s.setLocked(req.locked());
        }

        return toResponse(repo.save(s));
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
            s.getTimeslot() != null ? s.getTimeslot().getStartTime().toString() : null,
            s.getTimeslot() != null ? s.getTimeslot().getEndTime().toString() : null,
            s.getDuration(),
            s.isLocked()
        );
    }
}
