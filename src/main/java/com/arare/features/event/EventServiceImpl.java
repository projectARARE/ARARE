package com.arare.features.event;

import com.arare.common.enums.TimeslotType;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.solver.TimetableSolverService;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

        // 1. Block affected timeslots
        for (Timeslot slot : event.getAffectedTimeslots()) {
            slot.setType(TimeslotType.BLOCKED);
            timeslotRepo.save(slot);
        }

        // 2. Collect impacted session IDs
        List<Long> impacted = new ArrayList<>();

        for (var room : event.getAffectedRooms()) {
            sessionRepo.findUnlockedByScheduleIdAndRoomId(scheduleId, room.getId())
                .forEach(s -> impacted.add(s.getId()));
        }
        for (var teacher : event.getAffectedTeachers()) {
            sessionRepo.findUnlockedByScheduleIdAndTeacherId(scheduleId, teacher.getId())
                .forEach(s -> impacted.add(s.getId()));
        }

        // 3. Partial re-solve for impacted sessions
        if (!impacted.isEmpty()) {
            solverService.partialResolve(scheduleId, impacted.stream().distinct().toList());
        }
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
