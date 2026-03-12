package com.arare.features.building;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingServiceImpl implements BuildingService {

    private final BuildingRepository repo;
    private final RoomRepository roomRepo;
    private final ClassSessionRepository sessionRepo;

    @Override
    @Transactional
    public BuildingResponse create(BuildingRequest req) {
        Building b = Building.builder()
            .name(req.name())
            .location(req.location())
            .build();
        return toResponse(repo.save(b));
    }

    @Override
    @Transactional
    public BuildingResponse update(Long id, BuildingRequest req) {
        Building b = findEntity(id);
        b.setName(req.name());
        b.setLocation(req.location());
        return toResponse(repo.save(b));
    }

    @Override
    public BuildingResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<BuildingResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        // 1. Unassign any rooms in this building from scheduled sessions
        sessionRepo.clearRoomsByBuildingId(id);
        // 2. Delete rooms (Hibernate cleans up room_availability join table per room)
        roomRepo.deleteAll(roomRepo.findByBuildingId(id));
        // 3. Remove building from join tables (department_buildings, teacher_preferred_buildings)
        repo.removeDepartmentAssociations(id);
        repo.removeTeacherAssociations(id);
        // 4. Delete the building itself
        repo.deleteById(id);
    }

    private Building findEntity(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Building", id));
    }

    private BuildingResponse toResponse(Building b) {
        return new BuildingResponse(b.getId(), b.getName(), b.getLocation());
    }
}
