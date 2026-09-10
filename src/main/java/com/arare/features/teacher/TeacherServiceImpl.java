package com.arare.features.teacher;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.building.BuildingRepository;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.institute.Institute;
import com.arare.features.institute.InstituteRepository;
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
    private final ClassSessionRepository sessionRepo;
    private final CascadeDeletionService cascadeDeletionService;
    private final InstituteRepository instituteRepo;

    @Override
    @Transactional
    public TeacherResponse create(TeacherRequest req) {
        Teacher t = Teacher.builder()
            .employeeId(req.employeeId())
            .name(req.name())
            .subjects(resolveAll(subjectRepo, req.subjectIds(), "Subject"))
            .availableTimeslots(resolveAll(timeslotRepo, req.availableTimeslotIds(), "Timeslot"))
            .preferredBuildings(resolveAll(buildingRepo, req.preferredBuildingIds(), "Building"))
            .maxDailyHours(req.maxDailyHours())
            .maxWeeklyHours(req.maxWeeklyHours())
            .maxConsecutiveClasses(req.maxConsecutiveClasses())
            .movementPenalty(req.movementPenalty())
            .preferredFreeDay(req.preferredFreeDay())
            .institute(resolveInstitute(req.instituteId(), null))
            .build();
        return toResponse(repo.save(t));
    }

    @Override
    @Transactional
    public TeacherResponse update(Long id, TeacherRequest req) {
        Teacher t = findEntity(id);
        if (req.employeeId() != null) t.setEmployeeId(req.employeeId());
        t.setName(req.name());
        if (req.availableTimeslotIds() != null) t.setAvailableTimeslots(resolveAll(timeslotRepo, req.availableTimeslotIds(), "Timeslot"));
        if (req.preferredBuildingIds() != null) t.setPreferredBuildings(resolveAll(buildingRepo, req.preferredBuildingIds(), "Building"));
        if (req.subjectIds() != null) t.setSubjects(resolveAll(subjectRepo, req.subjectIds(), "Subject"));
        t.setMaxDailyHours(req.maxDailyHours());
        t.setMaxWeeklyHours(req.maxWeeklyHours());
        t.setMaxConsecutiveClasses(req.maxConsecutiveClasses());
        t.setMovementPenalty(req.movementPenalty());
        t.setPreferredFreeDay(req.preferredFreeDay());

        /**
         * Explicit instituteId always wins; otherwise keep an existing
         * institute or fall back to the single institute when there is none.
         */
        Institute inst = resolveInstitute(req.instituteId(), t);
        if (inst != t.getInstitute()) {
            t.setInstitute(inst);
        }

        /**
         * The subject mappings are a managed @ManyToMany collection on Teacher,
         * so Hibernate persists the join-table changes on save/flush.
         */

        return toResponse(repo.save(t));
    }

    @Override
    public TeacherResponse findById(Long id) { return toResponse(findEntity(id)); }

    @Override
    public List<TeacherResponse> findAll() {
        return repo.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        /**
         * Unassign from schedules, keep sessions
         */
        sessionRepo.clearTeacherById(id);   
        cascadeDeletionService.purgePreAllocationsForTeacher(id);
        cascadeDeletionService.detachTeacherFromEvents(id);
        repo.deleteById(id);
    }

    private Teacher findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
    }

    /**
     * Resolves every requested ID to its entity, failing with a 400-level
     * validation error instead of silently dropping unknown IDs.
     */
    private <T> List<T> resolveAll(org.springframework.data.jpa.repository.JpaRepository<T, Long> repo, List<Long> ids, String type) {
        if (ids == null) return List.of();
        List<T> found = repo.findAllById(ids);
        if (found.size() != new java.util.HashSet<>(ids).size()) {
            throw new IllegalArgumentException("One or more " + type + " ids do not exist: " + ids);
        }
        return found;
    }

    /**
     * Resolves the institute for a teacher. An explicit instituteId always
     * wins. When it is absent and the teacher has no institute yet, fall back
     * to the single institute if exactly one exists; otherwise (none or
     * multiple institutes) the teacher stays global (null). Never throws just
     * because instituteId is absent.
     */
    private Institute resolveInstitute(Long instituteId, Teacher teacher) {
        if (instituteId != null) {
            return instituteRepo.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute", instituteId));
        }
        if (teacher != null && teacher.getInstitute() != null) {
            return teacher.getInstitute();
        }
        List<Institute> all = instituteRepo.findAllByOrderByNameAsc();
        if (all.size() == 1) {
            return all.get(0);
        }
        return null;
    }

    private TeacherResponse toResponse(Teacher t) {
        List<Long> subjectIds = t.getSubjects().stream().map(s -> s.getId()).distinct().toList();
        List<String> subjectNames = t.getSubjects().stream().map(s -> s.getName()).distinct().toList();
        List<Long> availableTimeslotIds = t.getAvailableTimeslots().stream().map(ts -> ts.getId()).toList();
        List<Long> preferredBuildingIds = t.getPreferredBuildings().stream().map(b -> b.getId()).toList();
        return new TeacherResponse(
            t.getId(), t.getEmployeeId(), t.getName(),
            subjectIds, subjectNames,
            availableTimeslotIds, preferredBuildingIds,
            t.getMaxDailyHours(), t.getMaxWeeklyHours(), t.getMaxConsecutiveClasses(),
            t.getMovementPenalty(), t.getPreferredFreeDay(),
            t.getInstitute() != null ? t.getInstitute().getId() : null
        );
    }
}
