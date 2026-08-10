package com.arare.features.department;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.BatchRepository;
import com.arare.features.building.Building;
import com.arare.features.building.BuildingRepository;
import com.arare.features.building.BuildingResponse;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.subject.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository repo;
    private final BuildingRepository buildingRepo;
    private final ClassSessionRepository sessionRepo;
    private final ClassSectionRepository sectionRepo;
    private final BatchRepository batchRepo;
    private final SubjectRepository subjectRepo;
    private final CascadeDeletionService cascadeDeletionService;

    @Override
    @Transactional
    public DepartmentResponse create(DepartmentRequest req) {
        Department d = Department.builder()
            .name(req.name())
            .code(req.code())
            .buildingsAllowed(resolveBuildings(req.buildingIds()))
            .build();
        return toResponse(repo.save(d));
    }

    @Override
    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest req) {
        Department d = findEntity(id);
        d.setName(req.name());
        d.setCode(req.code());
        if (req.buildingIds() != null) {   // null = keep current buildings unchanged
            d.setBuildingsAllowed(resolveBuildings(req.buildingIds()));
        }
        return toResponse(repo.save(d));
    }

    @Override
    public DepartmentResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<DepartmentResponse> findAll() {
        return repo.findAllWithBuildings().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        // 1. Delete sessions referencing subjects of this department
        sessionRepo.deleteByDepartmentIdViaSubject(id);
        // 2. Delete sessions referencing batches of this department
        sessionRepo.deleteByDepartmentIdViaBatch(id);
        // 3. Delete class sections of batches in this department
        sectionRepo.deleteByDepartmentId(id);
        // 4. Delete batches
        batchRepo.deleteByDepartmentId(id);
        // 4b. Purge pre-allocations referencing this department's subjects/batches
        cascadeDeletionService.purgePreAllocationsForDepartment(id);
        // 5. Clean up teacher_subjects join table entries for subjects of this dept
        subjectRepo.removeTeacherAssociationsByDepartment(id);
        // 6. Delete subjects
        subjectRepo.deleteByDepartmentId(id);
        // 7. Delete the department (department_buildings join entries auto-removed by Hibernate
        //    because Department owns the @JoinTable)
        repo.deleteById(id);
    }

    private Department findEntity(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    // Resolves building IDs to entities, failing with a 400-level error when any
    // requested ID does not exist — consistent with TeacherServiceImpl.resolveAll().
    private List<Building> resolveBuildings(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Building> found = buildingRepo.findAllById(ids);
        if (found.size() != new java.util.HashSet<>(ids).size()) {
            throw new IllegalArgumentException("One or more Building ids do not exist: " + ids);
        }
        return found;
    }

    private DepartmentResponse toResponse(Department d) {
        List<BuildingResponse> buildings = d.getBuildingsAllowed().stream()
            .map(b -> new BuildingResponse(b.getId(), b.getName(), b.getLocation()))
            .toList();
        return new DepartmentResponse(d.getId(), d.getName(), d.getCode(), buildings);
    }
}
