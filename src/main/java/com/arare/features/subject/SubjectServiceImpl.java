package com.arare.features.subject;

import com.arare.exception.DuplicateResourceException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsession.ClassSessionRepository;
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
private final ClassSessionRepository sessionRepo;
private final CascadeDeletionService cascadeDeletionService;

    @Override
    @Transactional
    public SubjectResponse create(SubjectRequest req) {
        Department dept = resolveDepartment(req.departmentId());
        validateSubjectUniqueness(null, req.code(), dept);

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
        Department dept = resolveDepartment(req.departmentId());
        validateSubjectUniqueness(id, req.code(), dept);

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
        return repo.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    @Override
    public List<SubjectResponse> findByDepartment(Long departmentId) {
        return repo.findByDepartmentIdWithDetails(departmentId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        sessionRepo.deleteBySubjectId(id);
        cascadeDeletionService.purgePreAllocationsForSubject(id);
        /**
         * Clean up teacher_subjects join table
         */
        repo.removeTeacherAssociations(id);  
        repo.deleteById(id);
    }

    private Subject findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject", id));
    }

    /**
     * A null departmentId creates an institute-wide subject (no owning
     * department), offered to specific batches via SubjectOffering.
     */
    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepo.findById(departmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
    }

    /**
     * Validates subject code uniqueness: within a department when department is
     * set, or globally among institute-wide subjects when department is null.
     */
    private void validateSubjectUniqueness(Long existingId, String code, Department dept) {
        if (code == null || code.isBlank()) {
            return;
        }
        if (dept != null) {
            boolean exists = existingId == null
                ? repo.existsByDepartmentIdAndCode(dept.getId(), code)
                : repo.existsByDepartmentIdAndCodeAndIdNot(dept.getId(), code, existingId);
            if (exists) {
                throw new DuplicateResourceException(
                    "Subject with code '" + code + "' already exists in department '" + dept.getName() + "'");
            }
        } else {
            boolean exists = existingId == null
                ? repo.existsByCodeAndDepartmentIsNull(code)
                : repo.existsByCodeAndDepartmentIsNullAndIdNot(code, existingId);
            if (exists) {
                throw new DuplicateResourceException(
                    "Institute-wide subject with code '" + code + "' already exists");
            }
        }
    }

    private SubjectResponse toResponse(Subject s) {
        return new SubjectResponse(
            s.getId(), s.getName(), s.getCode(),
            s.getDepartment() != null ? s.getDepartment().getId() : null,
            s.getDepartment() != null ? s.getDepartment().getName() : null,
            s.getDepartment() != null && s.getDepartment().getInstitute() != null
                ? s.getDepartment().getInstitute().getId() : null,
            s.getWeeklyHours(), s.getChunkHours(),
            s.getRoomTypeRequired(), s.getLabSubtypeRequired(),
            s.isLab(), s.isRequiresTeacher(), s.isRequiresRoom(),
            s.getMinGapBetweenSessions(), s.getMaxSessionsPerDay()
        );
    }
}
