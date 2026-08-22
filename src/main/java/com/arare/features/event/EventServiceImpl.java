package com.arare.features.event;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.impact.DisruptionRequest;
import com.arare.features.impact.DisruptionService;
import com.arare.features.impact.DisruptionType;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.solvejob.SolveJobResponse;
import com.arare.features.solvejob.SolveJobService;
import com.arare.features.solver.DisruptionConstraintFact;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository repo;
    private final RoomRepository roomRepo;
    private final TeacherRepository teacherRepo;
    private final TimeslotRepository timeslotRepo;
    private final ScheduleRepository scheduleRepo;
    private final SolveJobService solveJobService;
    private final DisruptionService disruptionService;

    @Override
    @Transactional
    public EventResponse create(EventRequest req) {
        validateDateRange(req);
        Event e = Event.builder()
            .title(req.title())
            .type(req.type())
            .startDate(req.startDate())
            .endDate(req.endDate())
            .description(req.description())
            .affectedRooms(resolveRooms(req.affectedRoomIds()))
            .affectedTeachers(resolveTeachers(req.affectedTeacherIds()))
            .affectedTimeslots(resolveTimeslots(req.affectedTimeslotIds()))
            .build();
        return toResponse(repo.save(e));
    }

    @Override
    @Transactional
    public EventResponse update(Long id, EventRequest req) {
        validateDateRange(req);
        Event e = findEntity(id);
        e.setTitle(req.title());
        e.setType(req.type());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setDescription(req.description());
        if (req.affectedRoomIds() != null)     e.setAffectedRooms(resolveRooms(req.affectedRoomIds()));
        if (req.affectedTeacherIds() != null)  e.setAffectedTeachers(resolveTeachers(req.affectedTeacherIds()));
        if (req.affectedTimeslotIds() != null) e.setAffectedTimeslots(resolveTimeslots(req.affectedTimeslotIds()));
        return toResponse(repo.save(e));
    }

    @Override
    public EventResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<EventResponse> findAll() {
        return repo.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SolveJobResponse applyToSchedule(Long eventId, Long scheduleId) {
        Event event = findEntity(eventId);
        scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));

        Set<Long> impacted = new HashSet<>();
        List<LocalDate> dates = eventDates(event);

        for (LocalDate date : dates) {
            for (var room : event.getAffectedRooms()) {
                impacted.addAll(disruptionService.previewImpact(scheduleId,
                        new DisruptionRequest(DisruptionType.ROOM_UNAVAILABLE, room.getId(), date, event.getDescription()))
                    .impactedSessionIds());
            }
            for (var teacher : event.getAffectedTeachers()) {
                impacted.addAll(disruptionService.previewImpact(scheduleId,
                        new DisruptionRequest(DisruptionType.TEACHER_UNAVAILABLE, teacher.getId(), date, event.getDescription()))
                    .impactedSessionIds());
            }
            for (var timeslot : event.getAffectedTimeslots()) {
                impacted.addAll(disruptionService.previewImpact(scheduleId,
                        new DisruptionRequest(DisruptionType.TIMESLOT_BLOCKED, timeslot.getId(), date, event.getDescription()))
                    .impactedSessionIds());
            }
        }

        // If event is broad (no specific entities), treat it as a special event impact.
        if (event.getAffectedRooms().isEmpty()
            && event.getAffectedTeachers().isEmpty()
            && event.getAffectedTimeslots().isEmpty()) {
            for (LocalDate date : dates) {
                impacted.addAll(disruptionService.previewImpact(scheduleId,
                        new DisruptionRequest(DisruptionType.SPECIAL_EVENT, null, date, event.getDescription()))
                    .impactedSessionIds());
            }
        }

        if (impacted.isEmpty()) {
            log.info("Event {} applied to schedule {} — no sessions impacted", eventId, scheduleId);
            return solveJobService.completedNoop(scheduleId);
        }

        solveJobService.ensureNoActiveJobForSchedule(scheduleId);
        log.info("Event {} applied to schedule {} — re-solving {} sessions",
            eventId, scheduleId, impacted.size());
        return solveJobService.submitPartialResolve(scheduleId, impacted.stream().toList(), buildFacts(event));
    }

    /**
     * Converts an event into solver facts so the re-solve is forced to respect
     * the blocked rooms/teachers/timeslots across every affected date.
     *
     * <p>Room/teacher blocks only become facts on dates where the day is known;
     * a dayless block would wrongly force every session of that entity off the
     * timetable on all days.
     */
    private List<DisruptionConstraintFact> buildFacts(Event event) {
        List<DisruptionConstraintFact> facts = new ArrayList<>();
        for (LocalDate date : eventDates(event)) {
            String day = date != null ? date.getDayOfWeek().name() : null;
            if (day == null) continue;
            for (Room room : event.getAffectedRooms()) {
                facts.add(new DisruptionConstraintFact(
                    DisruptionType.ROOM_UNAVAILABLE, room.getId(), day));
            }
            for (Teacher teacher : event.getAffectedTeachers()) {
                facts.add(new DisruptionConstraintFact(
                    DisruptionType.TEACHER_UNAVAILABLE, teacher.getId(), day));
            }
        }
        for (Timeslot timeslot : event.getAffectedTimeslots()) {
            facts.add(new DisruptionConstraintFact(
                DisruptionType.TIMESLOT_BLOCKED, timeslot.getId(), null));
        }
        // Broad event (no specific entities): a special event on each date.
        if (event.getAffectedRooms().isEmpty()
            && event.getAffectedTeachers().isEmpty()
            && event.getAffectedTimeslots().isEmpty()) {
            for (LocalDate date : eventDates(event)) {
                String day = date != null ? date.getDayOfWeek().name() : null;
                if (day != null) {
                    facts.add(new DisruptionConstraintFact(
                        DisruptionType.SPECIAL_EVENT, null, day));
                }
            }
        }
        return facts;
    }

    private List<LocalDate> eventDates(Event event) {
        if (event.getStartDate() == null && event.getEndDate() == null) {
            return Collections.singletonList(null);
        }
        LocalDate start = event.getStartDate() != null ? event.getStartDate() : event.getEndDate();
        LocalDate end = event.getEndDate() != null ? event.getEndDate() : event.getStartDate();
        if (start == null || end == null) {
            return Collections.singletonList(null);
        }
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        List<LocalDate> dates = new ArrayList<>();
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            dates.add(cur);
            cur = cur.plusDays(1);
        }
        return dates;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    private void validateDateRange(EventRequest req) {
        if (req.startDate() != null && req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new IllegalArgumentException(
                "Event end date (" + req.endDate() + ") cannot be before start date (" + req.startDate() + ")"
            );
        }
    }

    private List<Room> resolveRooms(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return requireAllFound(roomRepo.findAllById(ids), ids, "Room");
    }

    private List<Teacher> resolveTeachers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return requireAllFound(teacherRepo.findAllById(ids), ids, "Teacher");
    }

    private List<Timeslot> resolveTimeslots(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return requireAllFound(timeslotRepo.findAllById(ids), ids, "Timeslot");
    }

    private <T> List<T> requireAllFound(List<T> found, List<Long> requested, String entity) {
        Set<Long> foundIds = found.stream()
            .map(o -> {
                if (o instanceof Room r) return r.getId();
                if (o instanceof Teacher t) return t.getId();
                if (o instanceof Timeslot t) return t.getId();
                throw new IllegalArgumentException("Unsupported entity type " + entity);
            })
            .collect(Collectors.toSet());
        List<Long> missing = requested.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException(entity, missing.get(0));
        }
        return found;
    }

    private Event findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));
    }

    private EventResponse toResponse(Event e) {
        return new EventResponse(
            e.getId(), e.getTitle(), e.getType(),
            e.getStartDate(), e.getEndDate(), e.getDescription(),
            e.getAffectedRooms().stream().map(r -> r.getId()).toList(),
            e.getAffectedTeachers().stream().map(t -> t.getId()).toList(),
            e.getAffectedTimeslots().stream().map(s -> s.getId()).toList()
        );
    }
}
