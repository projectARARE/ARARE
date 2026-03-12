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
        Timeslot t = Timeslot.builder()
            .day(req.day())
            .startTime(req.startTime())
            .endTime(req.endTime())
            .type(req.type())
            .build();
        return toResponse(repo.save(t));
    }

    @Override
    @Transactional
    public TimeslotResponse update(Long id, TimeslotRequest req) {
        Timeslot t = findEntity(id);
        t.setDay(req.day());
        t.setStartTime(req.startTime());
        t.setEndTime(req.endTime());
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
        return new TimeslotResponse(t.getId(), t.getDay(), t.getStartTime(), t.getEndTime(), t.getType());
    }
}
