package com.arare.features.batch;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.department.Department;
import com.arare.features.department.DepartmentRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchServiceImpl implements BatchService {

    private final BatchRepository repo;
    private final DepartmentRepository departmentRepo;
    private final RoomRepository roomRepo;
    private final ClassSessionRepository sessionRepo;
    private final ClassSectionRepository sectionRepo;
    private final SubjectRepository subjectRepo;
    private final CascadeDeletionService cascadeDeletionService;

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
            .homeRoom(req.homeRoomId() != null
                ? roomRepo.findById(req.homeRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", req.homeRoomId()))
                : null)
            .subjects(resolveSubjects(req.subjectIds()))
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
        b.setHomeRoom(req.homeRoomId() != null
            ? roomRepo.findById(req.homeRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", req.homeRoomId()))
            : null);
        if (req.subjectIds() != null) {
            b.setSubjects(resolveSubjects(req.subjectIds()));
        }
        return toResponse(repo.save(b));
    }

    // Resolves every requested subject id, rejecting unknown ones instead of
    // silently dropping curriculum entries.
    private List<Subject> resolveSubjects(List<Long> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Subject> found = subjectRepo.findAllById(subjectIds);
        if (found.size() != new HashSet<>(subjectIds).size()) {
            throw new IllegalArgumentException("One or more subject ids do not exist: " + subjectIds);
        }
        return found;
    }

    @Override
    public BatchResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<BatchResponse> findAll() {
        return repo.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    @Override
    public List<BatchResponse> findByDepartment(Long departmentId) {
        return repo.findByDepartmentIdWithDetails(departmentId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        // Delete sessions referencing this batch (directly or via its sections)
        List<Long> sectionIds = sectionRepo.findByBatchId(id).stream().map(s -> s.getId()).toList();
        for (Long sectionId : sectionIds) {
            sessionRepo.deleteBySectionId(sectionId);
        }
        sessionRepo.deleteByBatchId(id);
        cascadeDeletionService.purgePreAllocationsForBatch(id);
        sectionRepo.deleteByBatchId(id);
        repo.deleteById(id);
    }

    private Batch findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Batch", id));
    }

    private BatchResponse toResponse(Batch b) {
        List<Long> subjectIds = b.getSubjects().stream().map(s -> s.getId()).toList();
        List<String> subjectNames = b.getSubjects().stream().map(s -> s.getName()).toList();
        return new BatchResponse(
            b.getId(),
            b.getDepartment().getId(), b.getDepartment().getName(),
            b.getDepartment().getInstitute() != null ? b.getDepartment().getInstitute().getId() : null,
            b.getYear(), b.getSection(), b.getStudentCount(),
            b.getWorkingDays(), b.getPreferredFreeDay(),
            b.getHomeRoom() != null ? b.getHomeRoom().getId() : null,
            b.getHomeRoom() != null ? b.getHomeRoom().getRoomNumber() : null,
            subjectIds, subjectNames
        );
    }
}
