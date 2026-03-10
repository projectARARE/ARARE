package com.arare.features.preallocation;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.BatchRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Override
    @Transactional
    public PreAllocationResponse create(PreAllocationRequest req) {
        PreAllocation pa = PreAllocation.builder()
            .schedule(scheduleRepo.findById(req.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", req.scheduleId())))
            .batch(batchRepo.findById(req.batchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch", req.batchId())))
            .subject(subjectRepo.findById(req.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", req.subjectId())))
            .teacher(req.teacherId() != null
                ? teacherRepo.findById(req.teacherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher", req.teacherId()))
                : null)
            .room(req.roomId() != null
                ? roomRepo.findById(req.roomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", req.roomId()))
                : null)
            .timeslot(timeslotRepo.findById(req.timeslotId())
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", req.timeslotId())))
            .locked(req.locked())
            .build();
        return toResponse(repo.save(pa));
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
            pa.getTimeslot().getId(),
            pa.getTimeslot().getDay().toString(),
            pa.getTimeslot().getStartTime().toString(),
            pa.isLocked()
        );
    }
}
