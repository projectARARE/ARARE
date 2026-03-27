package com.arare.features.timeslot;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeslotServiceImpl implements TimeslotService {

    private final TimeslotRepository repo;
    private final ClassSessionRepository sessionRepo;

    @Override
    @Transactional
    public TimeslotResponse create(TimeslotRequest req) {
        validateTimeslotRequest(req, null);
        Integer resolvedSlotNumber = resolveSlotNumber(req, null);
        Timeslot t = Timeslot.builder()
            .day(req.day())
            .startTime(req.startTime())
            .endTime(req.endTime())
            .slotNumber(resolvedSlotNumber)
            .type(req.type())
            .build();
        return toResponse(repo.save(t));
    }

    @Override
    @Transactional
    public TimeslotResponse update(Long id, TimeslotRequest req) {
        validateTimeslotRequest(req, id);
        Integer resolvedSlotNumber = resolveSlotNumber(req, id);
        Timeslot t = findEntity(id);
        t.setDay(req.day());
        t.setStartTime(req.startTime());
        t.setEndTime(req.endTime());
        t.setSlotNumber(resolvedSlotNumber);
        t.setType(req.type());
        return toResponse(repo.save(t));
    }

    @Override
    public TimeslotResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<TimeslotResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        sessionRepo.clearTimeslotById(id);  // Unassign timeslot from sessions, keep sessions
        repo.deleteById(id);
    }

    private Timeslot findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Timeslot", id));
    }

    private TimeslotResponse toResponse(Timeslot t) {
        return new TimeslotResponse(t.getId(), t.getDay(), t.getStartTime(), t.getEndTime(), t.getSlotNumber(), t.getType());
    }

    private void validateTimeslotRequest(TimeslotRequest req, Long currentId) {
        if (!req.startTime().isBefore(req.endTime())) {
            throw new IllegalArgumentException("Timeslot startTime must be earlier than endTime.");
        }
        if (req.slotNumber() != null && req.slotNumber() <= 0) {
            throw new IllegalArgumentException("Timeslot slotNumber must be a positive integer.");
        }

        List<Timeslot> sameDay = repo.findAll().stream()
            .filter(t -> t.getDay() == req.day())
            .filter(t -> currentId == null || !t.getId().equals(currentId))
            .toList();

        boolean overlaps = sameDay.stream().anyMatch(t ->
            req.startTime().isBefore(t.getEndTime()) && t.getStartTime().isBefore(req.endTime()));

        if (overlaps) {
            throw new IllegalArgumentException(
                "Timeslot overlaps an existing slot on " + req.day() + ".");
        }

        if (req.slotNumber() != null) {
            boolean slotNumberConflict = sameDay.stream().anyMatch(t -> req.slotNumber().equals(t.getSlotNumber()));
            if (slotNumberConflict) {
                throw new IllegalArgumentException(
                    "Timeslot slotNumber already exists on " + req.day() + ".");
            }
        }
    }

    private Integer resolveSlotNumber(TimeslotRequest req, Long currentId) {
        if (req.slotNumber() != null) {
            return req.slotNumber();
        }
        return repo.findAll().stream()
            .filter(t -> t.getDay() == req.day())
            .filter(t -> currentId == null || !t.getId().equals(currentId))
            .map(Timeslot::getSlotNumber)
            .filter(n -> n != null)
            .max(Integer::compareTo)
            .orElse(0) + 1;
    }
}
