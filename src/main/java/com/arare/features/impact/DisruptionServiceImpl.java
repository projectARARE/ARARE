package com.arare.features.impact;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.solvejob.SolveJobResponse;
import com.arare.features.solvejob.SolveJobService;
import com.arare.features.solver.DisruptionConstraintFact;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisruptionServiceImpl implements DisruptionService {

    private final ScheduleRepository       scheduleRepo;
    private final ClassSessionRepository   sessionRepo;
    private final DependencyGraphBuilder   graphBuilder;
    private final ImpactAnalyzer           impactAnalyzer;
    private final SolveJobService          solveJobService;

    private final TeacherRepository        teacherRepo;
    private final RoomRepository           roomRepo;
    private final TimeslotRepository       timeslotRepo;

    @Override
    @Transactional(readOnly = true)
    public DisruptionResponse previewImpact(Long scheduleId, DisruptionRequest request) {
        validateSchedule(scheduleId);
        validateRequest(request);
        List<ClassSession> sessions = sessionRepo.findByScheduleId(scheduleId);

        // For SESSION_CANCELLED, applyDisruption only cancels the directly
        // targeted session (a single-session no-op); it does NOT run the BFS
        // expansion. Keep the preview consistent with that actual behaviour.
        Set<Long> impactedIds;
        if (request.type() == DisruptionType.SESSION_CANCELLED) {
            impactedIds = request.affectedEntityId() != null
                ? Set.of(request.affectedEntityId())
                : Set.of();
        } else {
            DependencyGraph graph = graphBuilder.build(sessions);
            impactedIds = impactAnalyzer.analyze(request, graph, sessions);
        }

        String entityName = resolveEntityName(request, sessions);
        List<DisruptionResponse.ImpactedSession> summaries = sessions.stream()
                .filter(s -> impactedIds.contains(s.getId()))
                .map(this::toImpactedSession)
                .toList();

        String disruption = buildDisruptionDescription(request, entityName);
        log.info("Disruption preview for schedule {}: type={}, affected={}, impacted={} sessions",
                scheduleId, request.type(), entityName, impactedIds.size());

        return new DisruptionResponse(
                request.type(),
                request.affectedEntityId(),
                entityName,
                disruption,
                impactedIds.size(),
                summaries,
                new ArrayList<>(impactedIds)
        );
    }

    @Override
    @Transactional
    public SolveJobResponse applyDisruption(Long scheduleId, DisruptionRequest request) {
        validateSchedule(scheduleId);
        validateRequest(request);

        // A cancelled session is removed from the timetable directly — the
        // solver cannot express "leave this session unplaced" because timeslot
        // is a mandatory planning variable, so routing it through a partial
        // resolve would always come back INFEASIBLE.
        if (request.type() == DisruptionType.SESSION_CANCELLED) {
            return cancelSession(scheduleId, request.affectedEntityId());
        }

        List<ClassSession> sessions = sessionRepo.findByScheduleId(scheduleId);

        DependencyGraph graph = graphBuilder.build(sessions);
        Set<Long> impactedIds = impactAnalyzer.analyze(request, graph, sessions);

        String entityName = resolveEntityName(request, sessions);
        log.info("Applying disruption to schedule {}: type={}, affected={}, re-solving {} sessions",
                scheduleId, request.type(), entityName, impactedIds.size());

        if (impactedIds.isEmpty()) {
            log.info("No sessions impacted by disruption — no re-solve needed");
            return solveJobService.completedNoop(scheduleId);
        }

        solveJobService.ensureNoActiveJobForSchedule(scheduleId);
        return solveJobService.submitPartialResolve(scheduleId, new ArrayList<>(impactedIds), buildFacts(request));
    }

    /**
     * Cancels a single session by clearing its assignment. The session row is
     * kept (it surfaces as an unplaced/orphan session in the UI) so the change
     * is visible and reversible by a later generate.
     */
    private SolveJobResponse cancelSession(Long scheduleId, Long sessionId) {
        ClassSession session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ClassSession", sessionId));
        if (session.getSchedule() == null || !session.getSchedule().getId().equals(scheduleId)) {
            throw new IllegalArgumentException("Session " + sessionId + " does not belong to schedule " + scheduleId);
        }
        sessionRepo.clearTimeslotForSession(sessionId);
        log.info("Session {} cancelled on schedule {}", sessionId, scheduleId);
        return solveJobService.completedNoop(scheduleId);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void validateSchedule(Long scheduleId) {
        scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));
    }

    /**
     * Converts a disruption request into solver facts so the re-solve is forced
     * to move affected sessions out of the blocked resource/time.
     *
     * <p>Teacher/room unavailability only becomes a fact when a day is known
     * (matching the impact analyzer, where a null date is effectively a no-op).
     * A dayless teacher/room fact would wrongly force every one of that
     * teacher's sessions off the timetable across all days.
     */
    private List<DisruptionConstraintFact> buildFacts(DisruptionRequest request) {
        String day = request.date() != null
            ? request.date().getDayOfWeek().name()
            : null;
        switch (request.type()) {
            case TIMESLOT_BLOCKED:
                return List.of(new DisruptionConstraintFact(
                    request.type(), request.affectedEntityId(), null));
            case SPECIAL_EVENT:
                if (day == null) return List.of();
                return List.of(new DisruptionConstraintFact(
                    DisruptionType.SPECIAL_EVENT, null, day));
            default: // TEACHER_UNAVAILABLE, ROOM_UNAVAILABLE
                if (day == null || request.affectedEntityId() == null) return List.of();
                return List.of(new DisruptionConstraintFact(
                    request.type(), request.affectedEntityId(), day));
        }
    }

    private void validateRequest(DisruptionRequest request) {
        boolean needsEntity = request.type() != DisruptionType.SPECIAL_EVENT;
        if (needsEntity && request.affectedEntityId() == null) {
            throw new IllegalArgumentException("affectedEntityId is required for disruption type " + request.type());
        }
    }

    private String resolveEntityName(DisruptionRequest request, List<ClassSession> sessions) {
        return switch (request.type()) {
            case TEACHER_UNAVAILABLE -> teacherRepo.findById(request.affectedEntityId())
                    .map(com.arare.features.teacher.Teacher::getName)
                    .orElse("Unknown Teacher");
            case ROOM_UNAVAILABLE -> roomRepo.findById(request.affectedEntityId())
                    .map(com.arare.features.room.Room::getRoomNumber)
                    .orElse("Unknown Room");
            case TIMESLOT_BLOCKED -> timeslotRepo.findById(request.affectedEntityId())
                    .map(ts -> ts.getDay() + " " + ts.getStartTime())
                    .orElse("Unknown Timeslot");
            case SESSION_CANCELLED -> sessions.stream()
                    .filter(s -> s.getId().equals(request.affectedEntityId()))
                    .findFirst()
                    .map(s -> s.getSubject().getName())
                    .orElse("Unknown Session");
            case SPECIAL_EVENT -> request.description() != null ? request.description() : "Special Event";
        };
    }

    private String buildDisruptionDescription(DisruptionRequest request, String entityName) {
        String dayInfo = request.date() != null ? " on " + request.date().getDayOfWeek().name() : "";
        return switch (request.type()) {
            case TEACHER_UNAVAILABLE -> "Teacher " + entityName + " unavailable" + dayInfo;
            case ROOM_UNAVAILABLE    -> "Room " + entityName + " unavailable" + dayInfo;
            case TIMESLOT_BLOCKED    -> "Timeslot " + entityName + " blocked";
            case SESSION_CANCELLED   -> "Session " + entityName + " cancelled";
            case SPECIAL_EVENT       -> entityName;
        };
    }

    private DisruptionResponse.ImpactedSession toImpactedSession(ClassSession s) {
        String batchLabel = null;
        if (s.getSection() != null) {
            batchLabel = s.getSection().getLabel();
        } else if (s.getBatch() != null) {
            batchLabel = s.getBatch().getDepartment().getName() + "-"
                    + s.getBatch().getYear() + s.getBatch().getSection();
        }
        return new DisruptionResponse.ImpactedSession(
                s.getId(),
                s.getSubject().getName(),
                batchLabel,
                s.getTeacher()  != null ? s.getTeacher().getName()        : null,
                s.getRoom()     != null ? s.getRoom().getRoomNumber()      : null,
                s.getTimeslot() != null ? s.getTimeslot().getDay().name()  : null,
                s.getTimeslot() != null ? s.getTimeslot().getStartTime().toString() : null,
                s.getTimeslot() != null ? s.getTimeslot().getEndTime().toString()   : null,
                s.isLocked()
        );
    }
}
