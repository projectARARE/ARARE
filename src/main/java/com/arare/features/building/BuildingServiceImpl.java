package com.arare.features.building;

import com.arare.exception.DuplicateResourceException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.BatchRepository;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.institute.Institute;
import com.arare.features.institute.InstituteRepository;
import com.arare.features.room.Room;
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
    private final BatchRepository batchRepo;
    private final CascadeDeletionService cascadeDeletionService;
    private final InstituteRepository instituteRepo;

    @Override
    @Transactional
    public BuildingResponse create(BuildingRequest req) {
        if (repo.existsByName(req.name())) {
            throw new DuplicateResourceException("Building '" + req.name() + "' already exists");
        }
        Building b = Building.builder()
            .name(req.name())
            .location(req.location())
            .institute(resolveInstitute(req.instituteId()))
            .build();
        return toResponse(repo.save(b));
    }

    @Override
    @Transactional
    public BuildingResponse update(Long id, BuildingRequest req) {
        Building b = findEntity(id);
        b.setName(req.name());
        b.setLocation(req.location());
        if (req.instituteId() != null) {
            b.setInstitute(resolveInstitute(req.instituteId()));
        }
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
        batchRepo.clearHomeRoomByBuildingId(id);
        sessionRepo.clearRoomsByBuildingId(id);
        for (Room room : roomRepo.findByBuildingId(id)) {
            cascadeDeletionService.purgePreAllocationsForRoom(room.getId());
            cascadeDeletionService.detachRoomFromEvents(room.getId());
        }
        roomRepo.deleteAll(roomRepo.findByBuildingId(id));
        repo.removeDepartmentAssociations(id);
        repo.removeTeacherAssociations(id);
        repo.deleteById(id);
    }

    private Building findEntity(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Building", id));
    }

    /**
     * Resolves the institute for a building. If an explicit instituteId is
     * provided it is used; otherwise exactly one institute must exist.
     */
    Institute resolveInstitute(Long instituteId) {
        if (instituteId != null) {
            return instituteRepo.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute", instituteId));
        }
        List<Institute> all = instituteRepo.findAll();
        if (all.size() == 1) {
            return all.get(0);
        }
        throw new IllegalArgumentException("instituteId is required when more than one institute exists");
    }

    private BuildingResponse toResponse(Building b) {
        return new BuildingResponse(b.getId(), b.getName(), b.getLocation(),
            b.getInstitute() != null ? b.getInstitute().getId() : null);
    }
}
