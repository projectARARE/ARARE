package com.arare.features.classsection;

import com.arare.exception.DuplicateResourceException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsession.ClassSessionRepository;
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
public class ClassSectionServiceImpl implements ClassSectionService {

    private final ClassSectionRepository repo;
    private final BatchRepository batchRepo;
    private final ClassSessionRepository sessionRepo;
    private final SubjectRepository subjectRepo;

    @Override
    @Transactional
    public ClassSectionResponse create(ClassSectionRequest req) {
        Batch batch = batchRepo.findById(req.batchId())
            .orElseThrow(() -> new ResourceNotFoundException("Batch", req.batchId()));

        if (repo.existsByBatchIdAndLabel(req.batchId(), req.label())) {
            throw new DuplicateResourceException(
                "Section '" + req.label() + "' already exists for this batch");
        }

        ClassSection cs = ClassSection.builder()
            .batch(batch)
            .label(req.label())
            .size(req.size())
            .subjects(resolveSubjects(req.subjectIds()))
            .build();
        return toResponse(repo.save(cs));
    }

    @Override
    @Transactional
    public List<ClassSectionResponse> createMany(ClassSectionBulkRequest req) {
        Batch batch = batchRepo.findById(req.batchId())
            .orElseThrow(() -> new ResourceNotFoundException("Batch", req.batchId()));

        List<String> existingLabels = repo.findByBatchId(req.batchId()).stream()
            .map(ClassSection::getLabel)
            .toList();

        String prefix = req.prefix().trim().toUpperCase();
        List<ClassSectionResponse> created = new ArrayList<>();
        for (int i = 1; i <= req.count(); i++) {
            String label = prefix + i;
            if (existingLabels.contains(label)) {
                continue; // idempotent: skip already-generated labels
            }
            ClassSection cs = ClassSection.builder()
                .batch(batch)
                .label(label)
                .size(req.size())
                .subjects(new ArrayList<>())
                .build();
            created.add(toResponse(repo.save(cs)));
        }
        if (created.isEmpty()) {
            throw new IllegalArgumentException(
                "All requested section labels already exist for this batch (" + prefix + "1.." + prefix + req.count() + ").");
        }
        return created;
    }

    @Override
    @Transactional
    public ClassSectionResponse update(Long id, ClassSectionRequest req) {
        ClassSection cs = findEntity(id);
        Batch batch = batchRepo.findById(req.batchId())
            .orElseThrow(() -> new ResourceNotFoundException("Batch", req.batchId()));
        cs.setBatch(batch);
        cs.setLabel(req.label());
        cs.setSize(req.size());
        if (req.subjectIds() != null) {
            cs.setSubjects(resolveSubjects(req.subjectIds()));
        }
        return toResponse(repo.save(cs));
    }

    @Override
    public ClassSectionResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<ClassSectionResponse> findAll() {
        return repo.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    @Override
    public List<ClassSectionResponse> findByBatch(Long batchId) {
        return repo.findByBatchIdWithDetails(batchId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        sessionRepo.deleteBySectionId(id);
        repo.deleteById(id);
    }

    private ClassSection findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("ClassSection", id));
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

    private ClassSectionResponse toResponse(ClassSection cs) {
        String batchName = cs.getBatch().getDepartment().getName()
            + " Yr" + cs.getBatch().getYear()
            + "-" + cs.getBatch().getSection();
        List<Long> subjectIds = cs.getSubjects().stream().map(s -> s.getId()).toList();
        List<String> subjectNames = cs.getSubjects().stream().map(s -> s.getName()).toList();
        return new ClassSectionResponse(
            cs.getId(), cs.getBatch().getId(), batchName, cs.getLabel(), cs.getSize(),
            subjectIds, subjectNames);
    }
}
