package com.arare.features.subject;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.department.Department;
import com.arare.features.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository repo;
    private final DepartmentRepository departmentRepo;

    @Override
    @Transactional
    public SubjectResponse create(SubjectRequest req) {
        Department dept = departmentRepo.findById(req.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));

        Subject s = Subject.builder()
            .name(req.name())
            .code(req.code())
            .department(dept)
            .weeklyHours(req.weeklyHours())
            .chunkHours(req.chunkHours())
            .roomTypeRequired(req.roomTypeRequired())
            .labSubtypeRequired(req.labSubtypeRequired())
            .isLab(req.isLab())
            .requiresTeacher(req.requiresTeacher())
            .requiresRoom(req.requiresRoom())
            .minGapBetweenSessions(req.minGapBetweenSessions())
            .maxSessionsPerDay(req.maxSessionsPerDay())
            .build();
        return toResponse(repo.save(s));
    }

    @Override
    @Transactional
    public SubjectResponse update(Long id, SubjectRequest req) {
        Subject s = findEntity(id);
        Department dept = departmentRepo.findById(req.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));

        s.setName(req.name());
        s.setCode(req.code());
        s.setDepartment(dept);
        s.setWeeklyHours(req.weeklyHours());
        s.setChunkHours(req.chunkHours());
        s.setRoomTypeRequired(req.roomTypeRequired());
        s.setLabSubtypeRequired(req.labSubtypeRequired());
        s.setLab(req.isLab());
        s.setRequiresTeacher(req.requiresTeacher());
        s.setRequiresRoom(req.requiresRoom());
        s.setMinGapBetweenSessions(req.minGapBetweenSessions());
        s.setMaxSessionsPerDay(req.maxSessionsPerDay());
        return toResponse(repo.save(s));
    }

    @Override
    public SubjectResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<SubjectResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<SubjectResponse> findByDepartment(Long departmentId) {
        return repo.findByDepartmentId(departmentId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    private Subject findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject", id));
    }

    private SubjectResponse toResponse(Subject s) {
        return new SubjectResponse(
            s.getId(), s.getName(), s.getCode(),
            s.getDepartment().getId(), s.getDepartment().getName(),
            s.getWeeklyHours(), s.getChunkHours(),
            s.getRoomTypeRequired(), s.getLabSubtypeRequired(),
            s.isLab(), s.isRequiresTeacher(), s.isRequiresRoom(),
            s.getMinGapBetweenSessions(), s.getMaxSessionsPerDay()
        );
    }
}
