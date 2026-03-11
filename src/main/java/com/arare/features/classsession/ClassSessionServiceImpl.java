package com.arare.features.classsession;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassSessionServiceImpl implements ClassSessionService {

    private final ClassSessionRepository repo;

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
            s.getSubject().getName(),
            s.getSubject().isLab(),
            s.getBatch() != null ? s.getBatch().getId() : null,
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
