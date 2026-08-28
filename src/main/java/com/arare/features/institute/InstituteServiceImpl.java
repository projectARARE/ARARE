package com.arare.features.institute;

import com.arare.exception.DuplicateResourceException;
import com.arare.exception.ResourceConflictException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstituteServiceImpl implements InstituteService {

    private final InstituteRepository repo;
    private final DepartmentRepository departmentRepo;

    @Override
    @Transactional
    public InstituteResponse create(InstituteRequest req) {
        if (repo.existsByName(req.name())) {
            throw new DuplicateResourceException("Institute '" + req.name() + "' already exists");
        }
        if (repo.existsByCode(req.code())) {
            throw new DuplicateResourceException("Institute with code '" + req.code() + "' already exists");
        }
        Institute i = Institute.builder()
            .name(req.name())
            .code(req.code())
            .description(req.description())
            .build();
        return toResponse(repo.save(i));
    }

    @Override
    @Transactional
    public InstituteResponse update(Long id, InstituteRequest req) {
        Institute i = findEntity(id);
        i.setName(req.name());
        i.setCode(req.code());
        i.setDescription(req.description());
        return toResponse(repo.save(i));
    }

    @Override
    public InstituteResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<InstituteResponse> findAll() {
        List<Institute> institutes = repo.findAllByOrderByNameAsc();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : departmentRepo.countGroupedByInstituteId()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return institutes.stream()
            .map(i -> toResponse(i, counts))
            .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Institute i = findEntity(id);
        long depts = departmentRepo.countByInstituteId(id);
        if (depts > 0) {
            throw new ResourceConflictException(
                "Institute has " + depts + " department(s). Move or delete them before deleting the institute.");
        }
        repo.delete(i);
    }

    private Institute findEntity(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Institute", id));
    }

    private InstituteResponse toResponse(Institute i) {
        return toResponse(i, Map.of(i.getId(), (long) departmentRepo.countByInstituteId(i.getId())));
    }

    private InstituteResponse toResponse(Institute i, Map<Long, Long> departmentCounts) {
        long depts = departmentCounts.getOrDefault(i.getId(), 0L);
        return new InstituteResponse(
            i.getId(),
            i.getName(),
            i.getCode(),
            i.getDescription(),
            (int) depts
        );
    }
}