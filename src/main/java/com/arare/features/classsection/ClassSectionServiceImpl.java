package com.arare.features.classsection;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassSectionServiceImpl implements ClassSectionService {

    private final ClassSectionRepository repo;
    private final BatchRepository batchRepo;

    @Override
    @Transactional
    public ClassSectionResponse create(ClassSectionRequest req) {
        Batch batch = batchRepo.findById(req.batchId())
            .orElseThrow(() -> new ResourceNotFoundException("Batch", req.batchId()));

        ClassSection cs = ClassSection.builder()
            .batch(batch)
            .label(req.label())
            .size(req.size())
            .build();
        return toResponse(repo.save(cs));
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
        return toResponse(repo.save(cs));
    }

    @Override
    public ClassSectionResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<ClassSectionResponse> findByBatch(Long batchId) {
        return repo.findByBatchId(batchId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    private ClassSection findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("ClassSection", id));
    }

    private ClassSectionResponse toResponse(ClassSection cs) {
        return new ClassSectionResponse(cs.getId(), cs.getBatch().getId(), cs.getLabel(), cs.getSize());
    }
}
