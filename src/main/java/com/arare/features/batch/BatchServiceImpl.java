package com.arare.features.batch;

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
public class BatchServiceImpl implements BatchService {

    private final BatchRepository repo;
    private final DepartmentRepository departmentRepo;

    @Override
    @Transactional
    public BatchResponse create(BatchRequest req) {
        Department dept = departmentRepo.findById(req.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));

        Batch b = Batch.builder()
            .department(dept)
            .year(req.year())
            .section(req.section())
            .studentCount(req.studentCount())
            .workingDays(req.workingDays() == null ? List.of() : req.workingDays())
            .preferredFreeDay(req.preferredFreeDay())
            .build();
        return toResponse(repo.save(b));
    }

    @Override
    @Transactional
    public BatchResponse update(Long id, BatchRequest req) {
        Batch b = findEntity(id);
        Department dept = departmentRepo.findById(req.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId()));

        b.setDepartment(dept);
        b.setYear(req.year());
        b.setSection(req.section());
        b.setStudentCount(req.studentCount());
        if (req.workingDays() != null) b.setWorkingDays(req.workingDays());
        b.setPreferredFreeDay(req.preferredFreeDay());
        return toResponse(repo.save(b));
    }

    @Override
    public BatchResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<BatchResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<BatchResponse> findByDepartment(Long departmentId) {
        return repo.findByDepartmentId(departmentId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    private Batch findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Batch", id));
    }

    private BatchResponse toResponse(Batch b) {
        return new BatchResponse(
            b.getId(),
            b.getDepartment().getId(), b.getDepartment().getName(),
            b.getYear(), b.getSection(), b.getStudentCount(),
            b.getWorkingDays(), b.getPreferredFreeDay()
        );
    }
}
