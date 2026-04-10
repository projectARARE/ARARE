package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.ScheduleStatus;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.solver.ScoreExplanationResponse;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.solver.TimetableSolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository repo;
    private final TimetableSolverService solverService;
    private final ClassSessionRepository sessionRepo;
    private final FeasibilityCheckService feasibilityCheckService;
    private final TimeslotRepository timeslotRepo;

    @Override
    @Transactional
    public ScheduleResponse generate(ScheduleRequest req) {
        FeasibilityCheckResult feasibility = feasibilityCheckService.check(req);
        if (!feasibility.feasible()) {
            String detail = feasibility.issues().stream()
                .filter(i -> i.severity() == FeasibilityIssue.Severity.ERROR)
                .map(FeasibilityIssue::message)
                .limit(3)
                .reduce((a, b) -> a + " | " + b)
                .orElse("Resolve feasibility errors before generating a schedule.");
            throw new IllegalStateException("Schedule request is infeasible: " + detail);
        }

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

        solverService.solveSchedule(schedule.getId(), req.departmentId(),
            req.batchIds(), req.teacherIds(), req.roomIds(), req.solvingTimeSeconds());

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
        sessionRepo.deleteByScheduleId(id);
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

    @Override
    @Transactional(readOnly = true)
    public List<ConflictSuggestionResponse> suggestFixes(Long scheduleId, Long sessionId, int limit) {
        findEntity(scheduleId);

        ClassSession target = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ClassSession", sessionId));
        if (target.getSchedule() == null || !target.getSchedule().getId().equals(scheduleId)) {
            throw new IllegalStateException("Session does not belong to schedule " + scheduleId);
        }

        List<ClassSession> sessions = sessionRepo.findByScheduleId(scheduleId);
        List<Timeslot> classTimeslots = timeslotRepo.findByType(com.arare.common.enums.TimeslotType.CLASS);

        int maxSuggestions = Math.max(1, limit);
        return classTimeslots.stream()
            .filter(slot -> target.getTimeslot() == null || !slot.getId().equals(target.getTimeslot().getId()))
            .map(slot -> buildSuggestion(target, slot, sessions))
            .sorted(Comparator
                .comparingInt(ConflictSuggestionResponse::hardConflicts)
                .thenComparingInt(ConflictSuggestionResponse::softPenalties)
                .thenComparing(ConflictSuggestionResponse::label))
            .limit(maxSuggestions)
            .toList();
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

    private ClassSessionResponse toSessionResponse(ClassSession cs) {
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
                cs.getSubject().getId(),
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

    private ConflictSuggestionResponse buildSuggestion(ClassSession target, Timeslot slot, List<ClassSession> sessions) {
        int hard = 0;
        int soft = 0;

        for (ClassSession other : sessions) {
            if (other.getId().equals(target.getId()) || other.getTimeslot() == null) {
                continue;
            }
            if (!other.getTimeslot().getId().equals(slot.getId())) {
                continue;
            }

            if (target.getTeacher() != null
                && other.getTeacher() != null
                && target.getTeacher().getId().equals(other.getTeacher().getId())) {
                hard += 1;
            }
            if (target.getRoom() != null
                && other.getRoom() != null
                && target.getRoom().getId().equals(other.getRoom().getId())) {
                hard += 1;
            }

            Long targetBatchId = effectiveBatchId(target);
            Long otherBatchId = effectiveBatchId(other);
            if (targetBatchId != null && otherBatchId != null && targetBatchId.equals(otherBatchId)) {
                hard += 1;
            }
        }

        Long targetBatchId = effectiveBatchId(target);
        for (ClassSession other : sessions) {
            if (other.getId().equals(target.getId()) || other.getTimeslot() == null) {
                continue;
            }
            if (targetBatchId == null || !targetBatchId.equals(effectiveBatchId(other))) {
                continue;
            }
            if (target.getSubject() != null
                && other.getSubject() != null
                && target.getSubject().getId().equals(other.getSubject().getId())
                && other.getTimeslot().getDay() == slot.getDay()) {
                soft += 1;
            }
        }

        String label = slot.getDay().name() + " " + slot.getStartTime() + "-" + slot.getEndTime();
        String preview = hard > 0
            ? hard + " hard conflict(s) remain"
            : "No hard conflicts, " + soft + " soft issue(s)";
        String scoreHint = hard > 0
            ? "HARD +" + hard
            : (soft > 0 ? "Soft +" + soft : "Best move");

        return new ConflictSuggestionResponse(
            slot.getId(),
            label,
            preview,
            scoreHint,
            hard,
            soft
        );
    }

    private Long effectiveBatchId(ClassSession session) {
        if (session.getBatch() != null) {
            return session.getBatch().getId();
        }
        if (session.getSection() != null && session.getSection().getBatch() != null) {
            return session.getSection().getBatch().getId();
        }
        return null;
    }
}
