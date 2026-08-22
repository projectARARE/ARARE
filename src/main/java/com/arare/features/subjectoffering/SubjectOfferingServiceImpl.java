package com.arare.features.subjectoffering;

import com.arare.exception.ResourceConflictException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectOfferingServiceImpl implements SubjectOfferingService {

    private final SubjectOfferingRepository repo;
    private final SubjectRepository subjectRepo;
    private final BatchRepository batchRepo;
    private final ClassSectionRepository sectionRepo;

    @Override
    @Transactional
    public SubjectOfferingResponse create(SubjectOfferingRequest req) {
        SubjectOffering offering = buildOffering(null, req);
        validateNoDuplicate(offering, null);
        return toResponse(repo.save(offering));
    }

    @Override
    @Transactional
    public SubjectOfferingResponse update(Long id, SubjectOfferingRequest req) {
        findEntity(id);
        SubjectOffering offering = buildOffering(id, req);
        validateNoDuplicate(offering, id);
        return toResponse(repo.save(offering));
    }

    @Override
    public SubjectOfferingResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<SubjectOfferingResponse> findAll() {
        return repo.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    @Override
    public List<SubjectOfferingResponse> findByBatch(Long batchId) {
        return repo.findByBatchId(batchId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<SubjectOfferingResponse> findBySection(Long sectionId) {
        return repo.findBySectionId(sectionId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<SubjectOfferingResponse> findBySubject(Long subjectId) {
        return repo.findBySubjectId(subjectId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    private SubjectOffering buildOffering(Long existingId, SubjectOfferingRequest req) {
        Subject subject = subjectRepo.findById(req.subjectId())
            .orElseThrow(() -> new ResourceNotFoundException("Subject", req.subjectId()));

        if ((req.batchId() == null) == (req.sectionId() == null)) {
            throw new IllegalArgumentException(
                "Exactly one of batchId or sectionId must be provided for a subject offering.");
        }

        Batch batch = null;
        if (req.batchId() != null) {
            batch = batchRepo.findById(req.batchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch", req.batchId()));
        }

        ClassSection section = null;
        if (req.sectionId() != null) {
            section = sectionRepo.findById(req.sectionId())
                .orElseThrow(() -> new ResourceNotFoundException("ClassSection", req.sectionId()));
            batch = section.getBatch();
        }

        return SubjectOffering.builder()
            .subject(subject)
            .batch(batch)
            .section(section)
            .weeklyHours(req.weeklyHours())
            .elective(req.elective())
            .build();
    }

    private void validateNoDuplicate(SubjectOffering candidate, Long excludedId) {
        List<SubjectOffering> others = candidate.getSection() != null
            ? repo.findBySectionId(candidate.getSection().getId())
            : repo.findByBatchId(candidate.getBatch().getId());

        for (SubjectOffering other : others) {
            if (excludedId != null && excludedId.equals(other.getId())) {
                continue;
            }
            if (other.getSubject().getId().equals(candidate.getSubject().getId())) {
                throw new ResourceConflictException(
                    "Subject '" + candidate.getSubject().getName()
                        + "' is already offered to " + scopeLabel(candidate) + ".");
            }
        }
    }

    private SubjectOffering findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("SubjectOffering", id));
    }

    private String batchLabel(Batch b) {
        return b.getDepartment().getCode() + "-" + b.getYear() + b.getSection();
    }

    private String scopeLabel(SubjectOffering o) {
        return o.getSection() != null
            ? "section " + batchLabel(o.getSection().getBatch()) + "-" + o.getSection().getLabel()
            : "batch " + batchLabel(o.getBatch());
    }

    private SubjectOfferingResponse toResponse(SubjectOffering o) {
        Batch batch = o.getBatch();
        ClassSection section = o.getSection();
        String batchLabel = batchLabel(batch);
        String sectionLabel = section != null ? batchLabel + "-" + section.getLabel() : null;
        return new SubjectOfferingResponse(
            o.getId(),
            o.getSubject().getId(), o.getSubject().getCode(), o.getSubject().getName(),
            batch.getId(), batchLabel,
            section != null ? section.getId() : null,
            sectionLabel,
            o.getWeeklyHours(), o.isElective()
        );
    }
}