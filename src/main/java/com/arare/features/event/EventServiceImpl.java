package com.arare.features.event;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.impact.DisruptionRequest;
import com.arare.features.impact.DisruptionService;
import com.arare.features.impact.DisruptionType;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.solver.TimetableSolverService;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository repo;
    private final RoomRepository roomRepo;
    private final TeacherRepository teacherRepo;
    private final TimeslotRepository timeslotRepo;
    private final ClassSessionRepository sessionRepo;
    private final ScheduleRepository scheduleRepo;
    private final TimetableSolverService solverService;
    private final DisruptionService disruptionService;

    @Override
    @Transactional
    public EventResponse create(EventRequest req) {
        Event e = Event.builder()
            .title(req.title())
            .type(req.type())
            .startDate(req.startDate())
            .endDate(req.endDate())
            .description(req.description())
            .affectedRooms(req.affectedRoomIds() == null ? List.of() : roomRepo.findAllById(req.affectedRoomIds()))
            .affectedTeachers(req.affectedTeacherIds() == null ? List.of() : teacherRepo.findAllById(req.affectedTeacherIds()))
            .affectedTimeslots(req.affectedTimeslotIds() == null ? List.of() : timeslotRepo.findAllById(req.affectedTimeslotIds()))
            .build();
        return toResponse(repo.save(e));
    }

    @Override
    @Transactional
    public EventResponse update(Long id, EventRequest req) {
        Event e = findEntity(id);
        e.setTitle(req.title());
        e.setType(req.type());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setDescription(req.description());
        if (req.affectedRoomIds() != null)     e.setAffectedRooms(roomRepo.findAllById(req.affectedRoomIds()));
        if (req.affectedTeacherIds() != null)  e.setAffectedTeachers(teacherRepo.findAllById(req.affectedTeacherIds()));
        if (req.affectedTimeslotIds() != null) e.setAffectedTimeslots(timeslotRepo.findAllById(req.affectedTimeslotIds()));
        return toResponse(repo.save(e));
    }

    @Override
    public EventResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<EventResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void applyToSchedule(Long eventId, Long scheduleId) {
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
            LocalDate date = dates.isEmpty() ? null : dates.get(0);
            impacted.addAll(disruptionService.previewImpact(scheduleId,
                    new DisruptionRequest(DisruptionType.SPECIAL_EVENT, null, date, event.getDescription()))
                .impactedSessionIds());
        }

        if (!impacted.isEmpty()) {
            solverService.partialResolve(scheduleId, impacted.stream().toList());
        }
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

        List<LocalDate> dates = new java.util.ArrayList<>();
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
