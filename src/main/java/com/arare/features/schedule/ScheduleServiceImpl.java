package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.ScheduleStatus;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.solver.TimetableSolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository repo;
    private final TimetableSolverService solverService;

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
        solverService.solveSchedule(schedule.getId());

        return toResponse(repo.findById(schedule.getId()).orElseThrow());
    }

    @Override
    public ScheduleResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
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
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

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
}
