package com.arare.features.teacherassignment;

import com.arare.exception.ResourceConflictException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

    private final TeacherAssignmentRepository repo;
    private final TeacherRepository teacherRepo;
    private final SubjectRepository subjectRepo;
    private final BatchRepository batchRepo;
    private final ClassSectionRepository sectionRepo;

    @Override
    @Transactional
    public TeacherAssignmentResponse create(TeacherAssignmentRequest req) {
        TeacherAssignment assignment = buildAssignment(null, req);
        validateNoDuplicate(assignment, null);
        return toResponse(repo.save(assignment));
    }

    @Override
    @Transactional
    public TeacherAssignmentResponse update(Long id, TeacherAssignmentRequest req) {
        findEntity(id);
        TeacherAssignment assignment = buildAssignment(id, req);
        validateNoDuplicate(assignment, id);
        return toResponse(repo.save(assignment));
    }

    @Override
    public TeacherAssignmentResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    public List<TeacherAssignmentResponse> findAll() {
        return repo.findAllWithDetails().stream().map(this::toResponse).toList();
    }

    @Override
    public List<TeacherAssignmentResponse> findByTeacher(Long teacherId) {
        return repo.findByTeacherIdWithDetails(teacherId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<TeacherAssignmentResponse> findByBatch(Long batchId) {
        return repo.findByBatchId(batchId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<TeacherAssignmentResponse> findBySubject(Long subjectId) {
        return repo.findBySubjectId(subjectId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findEntity(id);
        repo.deleteById(id);
    }

    /**
     * Resolves the persisted catalogs for a request and applies the same rules
     * a solver HARD constraint would: the teacher must be qualified for the
     * subject, and the allotment scope (batch XOR section) must be coherent.
     * {@code existingId} lets an update skip its own id in duplicate checks.
     */
    private TeacherAssignment buildAssignment(Long existingId, TeacherAssignmentRequest req) {
        Teacher teacher = teacherRepo.findById(req.teacherId())
            .orElseThrow(() -> new ResourceNotFoundException("Teacher", req.teacherId()));
        Subject subject = subjectRepo.findById(req.subjectId())
            .orElseThrow(() -> new ResourceNotFoundException("Subject", req.subjectId()));

        if (!subject.isRequiresTeacher()) {
            throw new IllegalArgumentException(
                "Subject '" + subject.getName() + "' does not require a teacher and cannot be allotted.");
        }
        if (teacher.getSubjects().stream().noneMatch(ts -> ts.getId().equals(subject.getId()))) {
            throw new IllegalArgumentException(
                "Teacher '" + teacher.getName() + "' is not qualified to teach subject '" + subject.getName()
                    + "'. Add the subject to the teacher's profile before allotting it.");
        }
        if ((req.batchId() == null) == (req.sectionId() == null)) {
            throw new IllegalArgumentException(
                "Exactly one of batchId or sectionId must be provided for a teacher assignment.");
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
            // A section always belongs to exactly one batch; when the request
            // only names a section the batch is inferred from it.
            batch = section.getBatch();
        }

        int priority = req.priority() != null ? req.priority() : 1;
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be >= 0");
        }

        return TeacherAssignment.builder()
            .teacher(teacher)
            .subject(subject)
            .batch(batch)
            .section(section)
            .weeklyHours(req.weeklyHours())
            .priority(priority)
            .notes(req.notes())
            .build();
    }

    /**
     * Enforces the one-teacher-per-(subject, scope) rule the solver's
     * {@code singleTeacherPerSubjectSection} HARD constraint assumes. When a
     * section has its own allotment the batch-level allotment for the same
     * subject is allowed to differ (each section may have its own lab
     * teacher), so duplicates are only rejected within the same scope key.
     */
    private void validateNoDuplicate(TeacherAssignment candidate, Long excludedId) {
        Long subjectId = candidate.getSubject().getId();
        List<TeacherAssignment> others = new ArrayList<>();

        if (candidate.getSection() != null) {
            others.addAll(repo.findBySectionIdIn(List.of(candidate.getSection().getId())));
        } else {
            others.addAll(repo.findByBatchId(candidate.getBatch().getId()));
        }

        for (TeacherAssignment other : others) {
            if (excludedId != null && excludedId.equals(other.getId())) {
                continue;
            }
            boolean sameScope = candidate.getSection() != null
                ? candidate.getSection().getId().equals(other.getSection().getId())
                : other.getSection() == null
                    && candidate.getBatch().getId().equals(other.getBatch().getId());
            if (sameScope && other.getSubject().getId().equals(subjectId)) {
                throw new ResourceConflictException(
                    "Subject '" + candidate.getSubject().getName() + "' is already allotted to '"
                        + other.getTeacher().getName() + "' for "
                        + scopeLabel(candidate) + ". A subject must be taught by exactly one teacher per class.");
            }
        }
    }

    private TeacherAssignment findEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("TeacherAssignment", id));
    }

    private String scopeLabel(TeacherAssignment a) {
        if (a.getSection() != null) {
            return "section " + batchLabel(a.getSection().getBatch()) + "-" + a.getSection().getLabel();
        }
        return "batch " + batchLabel(a.getBatch());
    }

    private static String batchLabel(Batch b) {
        return b.getDepartment().getCode() + "-" + b.getYear() + b.getSection();
    }

    private TeacherAssignmentResponse toResponse(TeacherAssignment a) {
        Batch batch = a.getBatch();
        ClassSection section = a.getSection();
        String batchLabel = batchLabel(batch);
        String sectionLabel = section != null ? batchLabel + "-" + section.getLabel() : null;
        return new TeacherAssignmentResponse(
            a.getId(),
            a.getTeacher().getId(), a.getTeacher().getName(),
            a.getSubject().getId(), a.getSubject().getCode(), a.getSubject().getName(),
            batch.getId(), batchLabel,
            section != null ? section.getId() : null,
            sectionLabel,
            a.getWeeklyHours(), a.getPriority(), a.getNotes()
        );
    }
}