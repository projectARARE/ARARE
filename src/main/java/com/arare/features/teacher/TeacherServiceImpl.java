package com.arare.features.teacher;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.building.BuildingRepository;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherServiceImpl implements TeacherService {

    private final TeacherRepository repo;
    private final SubjectRepository subjectRepo;
    private final TimeslotRepository timeslotRepo;
    private final BuildingRepository buildingRepo;

    @Override
    @Transactional
    public TeacherResponse create(TeacherRequest req) {
        Teacher t = Teacher.builder()
            .name(req.name())
            .subjects(req.subjectIds() == null ? List.of() : subjectRepo.findAllById(req.subjectIds()))
            .availableTimeslots(req.availableTimeslotIds() == null ? List.of() : timeslotRepo.findAllById(req.availableTimeslotIds()))
            .preferredBuildings(req.preferredBuildingIds() == null ? List.of() : buildingRepo.findAllById(req.preferredBuildingIds()))
            .maxDailyHours(req.maxDailyHours())
            .maxWeeklyHours(req.maxWeeklyHours())
            .maxConsecutiveClasses(req.maxConsecutiveClasses())
            .movementPenalty(req.movementPenalty())
            .preferredFreeDay(req.preferredFreeDay())
            .build();
        return toResponse(repo.save(t));
    }

    @Override
    @Transactional
    public TeacherResponse update(Long id, TeacherRequest req) {
        Teacher t = findEntity(id);
        t.setName(req.name());
        if (req.subjectIds() != null)           t.setSubjects(subjectRepo.findAllById(req.subjectIds()));
        if (req.availableTimeslotIds() != null)  t.setAvailableTimeslots(timeslotRepo.findAllById(req.availableTimeslotIds()));
        if (req.preferredBuildingIds() != null)  t.setPreferredBuildings(buildingRepo.findAllById(req.preferredBuildingIds()));
        t.setMaxDailyHours(req.maxDailyHours());
        t.setMaxWeeklyHours(req.maxWeeklyHours());
        t.setMaxConsecutiveClasses(req.maxConsecutiveClasses());
        t.setMovementPenalty(req.movementPenalty());
        t.setPreferredFreeDay(req.preferredFreeDay());
        return toResponse(repo.save(t));
    }

    @Override
    public TeacherResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<TeacherResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    private Teacher findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
    }

    private TeacherResponse toResponse(Teacher t) {
        List<Long> subjectIds = t.getSubjects().stream().map(s -> s.getId()).toList();
        List<String> subjectNames = t.getSubjects().stream().map(s -> s.getName()).toList();
        return new TeacherResponse(
            t.getId(), t.getName(),
            subjectIds, subjectNames,
            t.getMaxDailyHours(), t.getMaxWeeklyHours(), t.getMaxConsecutiveClasses(),
            t.getMovementPenalty(), t.getPreferredFreeDay()
        );
    }
}
