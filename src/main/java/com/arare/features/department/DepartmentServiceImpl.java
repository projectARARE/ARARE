package com.arare.features.department;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.building.Building;
import com.arare.features.building.BuildingRepository;
import com.arare.features.building.BuildingResponse;
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

    @Override
    @Transactional
    public DepartmentResponse create(DepartmentRequest req) {
        Department d = Department.builder()
            .name(req.name())
            .buildingsAllowed(resolveBuildings(req.buildingIds()))
            .build();
        return toResponse(repo.save(d));
    }

    @Override
    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest req) {
        Department d = findEntity(id);
        d.setName(req.name());
        d.setBuildingsAllowed(resolveBuildings(req.buildingIds()));
        return toResponse(repo.save(d));
    }

    @Override
    public DepartmentResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<DepartmentResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    private Department findEntity(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private List<Building> resolveBuildings(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return buildingRepo.findAllById(ids);
    }

    private DepartmentResponse toResponse(Department d) {
        List<BuildingResponse> buildings = d.getBuildingsAllowed().stream()
            .map(b -> new BuildingResponse(b.getId(), b.getName(), b.getLocation()))
            .toList();
        return new DepartmentResponse(d.getId(), d.getName(), buildings);
    }
}
