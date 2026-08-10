package com.arare.features.room;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.building.Building;
import com.arare.features.building.BuildingRepository;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomServiceImpl implements RoomService {

    private final RoomRepository repo;
    private final BuildingRepository buildingRepo;
    private final TimeslotRepository timeslotRepo;
    private final ClassSessionRepository sessionRepo;
    private final CascadeDeletionService cascadeDeletionService;

    @Override
    @Transactional
    public RoomResponse create(RoomRequest req) {
        Building building = buildingRepo.findById(req.buildingId())
            .orElseThrow(() -> new ResourceNotFoundException("Building", req.buildingId()));

        List<Timeslot> slots = req.availableTimeslotIds() == null
            ? List.of()
            : resolveAll(timeslotRepo, req.availableTimeslotIds(), "Timeslot");

        Room room = Room.builder()
            .building(building)
            .roomNumber(req.roomNumber())
            .type(req.type())
            .labSubtype(req.labSubtype())
            .capacity(req.capacity())
            .availableTimeslots(slots)
            .build();
        return toResponse(repo.save(room));
    }

    @Override
    @Transactional
    public RoomResponse update(Long id, RoomRequest req) {
        Room room = findEntity(id);
        Building building = buildingRepo.findById(req.buildingId())
            .orElseThrow(() -> new ResourceNotFoundException("Building", req.buildingId()));

        room.setBuilding(building);
        room.setRoomNumber(req.roomNumber());
        room.setType(req.type());
        room.setLabSubtype(req.labSubtype());
        room.setCapacity(req.capacity());

        if (req.availableTimeslotIds() != null) {
            room.setAvailableTimeslots(resolveAll(timeslotRepo, req.availableTimeslotIds(), "Timeslot"));
        }
        return toResponse(repo.save(room));
    }

    @Override
    public RoomResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<RoomResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<RoomResponse> findByBuilding(Long buildingId) {
        return repo.findByBuildingId(buildingId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        sessionRepo.clearRoomById(id);   // Unassign room from sessions, keep sessions
        cascadeDeletionService.purgePreAllocationsForRoom(id);
        cascadeDeletionService.detachRoomFromEvents(id);
        repo.deleteById(id);
    }

    private Room findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room", id));
    }

    // Resolves every requested ID to its entity, failing with a 400-level
    // validation error instead of silently dropping unknown IDs.
    private <T> List<T> resolveAll(org.springframework.data.jpa.repository.JpaRepository<T, Long> repo, List<Long> ids, String type) {
        if (ids == null) return List.of();
        List<T> found = repo.findAllById(ids);
        if (found.size() != new java.util.HashSet<>(ids).size()) {
            throw new IllegalArgumentException("One or more " + type + " ids do not exist: " + ids);
        }
        return found;
    }

    private RoomResponse toResponse(Room r) {
        List<Long> availableTimeslotIds = r.getAvailableTimeslots().stream().map(ts -> ts.getId()).toList();
        return new RoomResponse(
            r.getId(),
            r.getBuilding().getId(),
            r.getBuilding().getName(),
            r.getRoomNumber(),
            r.getType(),
            r.getLabSubtype(),
            r.getCapacity(),
            availableTimeslotIds
        );
    }
}
