package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.ScheduleStatus;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.solver.ScoreExplanationResponse;
import com.arare.features.solver.TimetableSolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository repo;
    private final TimetableSolverService solverService;
    private final ClassSessionRepository sessionRepo;

    @Override
    @Transactional
    public ScheduleResponse generate(ScheduleRequest req) {
        Schedule parent = req.parentScheduleId() != null
            ? repo.findById(req.parentScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", req.parentScheduleId()))
            : null;

        Schedule schedule = Schedule.builder()
            .name(req.name())
            .scope(req.scope() != null ? req.scope() : ScheduleScope.DEPARTMENT)
            .status(ScheduleStatus.DRAFT)
            .parentSchedule(parent)
            .build();
        schedule = repo.save(schedule);

        // Run solver synchronously; for long runs consider @Async or a job queue
        solverService.solveSchedule(schedule.getId(), req.departmentId());

        return toResponse(repo.findById(schedule.getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ScheduleResponse partialResolve(Long scheduleId, List<Long> impactedSessionIds) {
        findEntity(scheduleId); // validate exists
        solverService.partialResolve(scheduleId, impactedSessionIds);
        return toResponse(repo.findById(scheduleId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public ScoreExplanationResponse explainScore(Long scheduleId) {
        findEntity(scheduleId); // validate exists
        return solverService.explainSchedule(scheduleId);
    }

    @Override
    @Transactional(readOnly = true)
    public String getExplanation(Long id) {
        String explanation = findEntity(id).getScoreExplanation();
        return explanation != null ? explanation : "No explanation available.";
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> getSessionsBySchedule(Long scheduleId) {
        findEntity(scheduleId); // validate schedule exists
        return sessionRepo.findByScheduleId(scheduleId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private Schedule findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
    }

    private ScheduleResponse toResponse(Schedule s) {
        return new ScheduleResponse(
            s.getId(), s.getName(), s.getScope(), s.getStatus(),
            s.getParentSchedule() != null ? s.getParentSchedule().getId() : null,
            s.getScore(), s.getScoreExplanation(),
            s.getCreatedAt() != null ? s.getCreatedAt().toString() : null
        );
    }

    private ClassSessionResponse toSessionResponse(ClassSession cs) {
        // Construct a human-readable batch/section label
        String batchLabel = null;
        if (cs.getSection() != null) {
            batchLabel = cs.getSection().getLabel();
        } else if (cs.getBatch() != null) {
            Batch b = cs.getBatch();
            batchLabel = b.getDepartment().getName() + "-" + b.getYear() + b.getSection();
        }

        Long teacherId   = cs.getTeacher() != null ? cs.getTeacher().getId()      : null;
        String teacherName = cs.getTeacher() != null ? cs.getTeacher().getName()  : null;

        Long   roomId      = cs.getRoom() != null ? cs.getRoom().getId()           : null;
        String roomNumber  = cs.getRoom() != null ? cs.getRoom().getRoomNumber()   : null;
        String buildingName = cs.getRoom() != null ? cs.getRoom().getBuilding().getName() : null;

        Long   timeslotId = cs.getTimeslot() != null ? cs.getTimeslot().getId()                    : null;
        String day        = cs.getTimeslot() != null ? cs.getTimeslot().getDay().name()            : null;
        String startTime  = cs.getTimeslot() != null ? cs.getTimeslot().getStartTime().toString()  : null;
        String endTime    = cs.getTimeslot() != null ? cs.getTimeslot().getEndTime().toString()    : null;

        return new ClassSessionResponse(
                cs.getId(),
                cs.getSubject().getName(),
                cs.getSubject().isLab(),
                cs.getBatch() != null ? cs.getBatch().getId() : null,
                cs.getSection() != null ? cs.getSection().getId() : null,
                batchLabel,
                teacherId,
                teacherName,
                roomId,
                roomNumber,
                buildingName,
                timeslotId,
                day,
                startTime,
                endTime,
                cs.getDuration(),
                cs.isLocked()
        );
    }
}
