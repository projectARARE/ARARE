package com.arare.features.academicterm;

import com.arare.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicTermServiceImpl implements AcademicTermService {

    private final AcademicTermRepository repo;

    @Override
    @Transactional(readOnly = true)
    public List<AcademicTermResponse> findAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicTermResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public AcademicTermResponse create(AcademicTermRequest req) {
        AcademicTerm term = AcademicTerm.builder()
                .name(req.name())
                .academicYear(req.academicYear())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .examPeriodStart(req.examPeriodStart())
                .examPeriodEnd(req.examPeriodEnd())
                .status(req.status() != null ? req.status() : com.arare.common.enums.AcademicTermStatus.UPCOMING)
                .description(req.description())
                .build();
        return toResponse(repo.save(term));
    }

    @Override
    @Transactional
    public AcademicTermResponse update(Long id, AcademicTermRequest req) {
        AcademicTerm term = findEntity(id);
        term.setName(req.name());
        term.setAcademicYear(req.academicYear());
        term.setStartDate(req.startDate());
        term.setEndDate(req.endDate());
        term.setExamPeriodStart(req.examPeriodStart());
        term.setExamPeriodEnd(req.examPeriodEnd());
        if (req.status() != null) term.setStatus(req.status());
        term.setDescription(req.description());
        return toResponse(repo.save(term));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    // ------------------------------------------------------------------

    private AcademicTerm findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("AcademicTerm", id));
    }

    private AcademicTermResponse toResponse(AcademicTerm t) {
        return new AcademicTermResponse(
                t.getId(),
                t.getName(),
                t.getAcademicYear(),
                t.getStartDate(),
                t.getEndDate(),
                t.getExamPeriodStart(),
                t.getExamPeriodEnd(),
                t.getStatus(),
                t.getDescription(),
                t.getCreatedAt() != null ? t.getCreatedAt().toString() : null
        );
    }
}
