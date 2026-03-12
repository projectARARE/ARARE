package com.arare.features.impact;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.schedule.ScheduleResponse;
import com.arare.features.schedule.ScheduleService;
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
    private final ScheduleService          scheduleService;

    @Override
    @Transactional(readOnly = true)
    public DisruptionResponse previewImpact(Long scheduleId, DisruptionRequest request) {
        validateSchedule(scheduleId);
        List<ClassSession> sessions = sessionRepo.findByScheduleId(scheduleId);

        DependencyGraph graph = graphBuilder.build(sessions);
        Set<Long> impactedIds = impactAnalyzer.analyze(request, graph, sessions);

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
    public ScheduleResponse applyDisruption(Long scheduleId, DisruptionRequest request) {
        validateSchedule(scheduleId);
        List<ClassSession> sessions = sessionRepo.findByScheduleId(scheduleId);

        DependencyGraph graph = graphBuilder.build(sessions);
        Set<Long> impactedIds = impactAnalyzer.analyze(request, graph, sessions);

        String entityName = resolveEntityName(request, sessions);
        log.info("Applying disruption to schedule {}: type={}, affected={}, re-solving {} sessions",
                scheduleId, request.type(), entityName, impactedIds.size());

        if (impactedIds.isEmpty()) {
            log.info("No sessions impacted by disruption — no re-solve needed");
            return scheduleService.findById(scheduleId);
        }

        return scheduleService.partialResolve(scheduleId, new ArrayList<>(impactedIds));
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void validateSchedule(Long scheduleId) {
        scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));
    }

    private String resolveEntityName(DisruptionRequest request, List<ClassSession> sessions) {
        return switch (request.type()) {
            case TEACHER_UNAVAILABLE -> sessions.stream()
                    .filter(s -> s.getTeacher() != null && s.getTeacher().getId().equals(request.affectedEntityId()))
                    .findFirst()
                    .map(s -> s.getTeacher().getName())
                    .orElse("Unknown Teacher");
            case ROOM_UNAVAILABLE -> sessions.stream()
                    .filter(s -> s.getRoom() != null && s.getRoom().getId().equals(request.affectedEntityId()))
                    .findFirst()
                    .map(s -> s.getRoom().getRoomNumber())
                    .orElse("Unknown Room");
            case TIMESLOT_BLOCKED -> sessions.stream()
                    .filter(s -> s.getTimeslot() != null && s.getTimeslot().getId().equals(request.affectedEntityId()))
                    .findFirst()
                    .map(s -> s.getTimeslot().getDay() + " " + s.getTimeslot().getStartTime())
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
