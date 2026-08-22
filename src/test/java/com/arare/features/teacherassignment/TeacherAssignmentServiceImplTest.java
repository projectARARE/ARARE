package com.arare.features.teacherassignment;

import com.arare.exception.ResourceConflictException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.department.Department;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherAssignmentServiceImplTest {

    @Mock private TeacherAssignmentRepository repo;
    @Mock private TeacherRepository teacherRepo;
    @Mock private SubjectRepository subjectRepo;
    @Mock private BatchRepository batchRepo;
    @Mock private ClassSectionRepository sectionRepo;

    @InjectMocks private TeacherAssignmentServiceImpl service;

    private Teacher qualifiedTeacher() {
        Subject dsa = subject(10L, "DSA", true);
        Teacher t = Teacher.builder().name("Dr. Meena").build();
        t.setId(1L);
        t.setSubjects(List.of(dsa));
        return t;
    }

    private Subject subject(Long id, String name, boolean requiresTeacher) {
        Subject s = Subject.builder().name(name).code("SUB").requiresTeacher(requiresTeacher).build();
        s.setId(id);
        return s;
    }

    private Department dept() {
        Department d = new Department();
        d.setId(90L);
        d.setCode("CSE");
        d.setName("Computer Science");
        return d;
    }

    private Batch batch(Long id) {
        Batch b = Batch.builder().department(dept()).year(2).section("A").studentCount(60).build();
        b.setId(id);
        return b;
    }

    private ClassSection section(Long id, Batch batch) {
        ClassSection cs = ClassSection.builder().batch(batch).label("A").size(30).build();
        cs.setId(id);
        return cs;
    }

    @Test
    void createRejectsUnqualifiedTeacher() {
        when(teacherRepo.findById(1L)).thenReturn(Optional.of(qualifiedTeacher()));
        Subject os = subject(11L, "OS", true);
        when(subjectRepo.findById(11L)).thenReturn(Optional.of(os));

        TeacherAssignmentRequest req = new TeacherAssignmentRequest(1L, 11L, 5L, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void createRejectsMissingScope() {
        when(teacherRepo.findById(1L)).thenReturn(Optional.of(qualifiedTeacher()));
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject(10L, "DSA", true)));

        TeacherAssignmentRequest noScope = new TeacherAssignmentRequest(1L, 10L, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(noScope));

        TeacherAssignmentRequest twoScopes = new TeacherAssignmentRequest(1L, 10L, 5L, 7L, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(twoScopes));
    }

    @Test
    void createResolvesBatchFromSectionScope() {
        when(teacherRepo.findById(1L)).thenReturn(Optional.of(qualifiedTeacher()));
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject(10L, "DSA", true)));
        Batch batch = batch(5L);
        when(sectionRepo.findById(7L)).thenReturn(Optional.of(section(7L, batch)));
        when(repo.findBySectionIdIn(List.of(7L))).thenReturn(List.of());
        when(repo.save(any(TeacherAssignment.class))).thenAnswer(inv -> {
            TeacherAssignment a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        // A section-only request infers the batch from the section.
        TeacherAssignmentRequest req = new TeacherAssignmentRequest(1L, 10L, null, 7L, null, null, null);
        TeacherAssignmentResponse response = service.create(req);

        assertEquals(5L, response.batchId());
        assertEquals("CSE-2A", response.batchLabel());
        assertEquals("CSE-2A-A", response.sectionLabel());
    }

    @Test
    void createRejectsDuplicateWithinSameBatch() {
        when(teacherRepo.findById(1L)).thenReturn(Optional.of(qualifiedTeacher()));
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject(10L, "DSA", true)));
        Batch batch = batch(5L);
        when(batchRepo.findById(5L)).thenReturn(Optional.of(batch));

        Teacher other = Teacher.builder().name("Dr. Ravi").build();
        other.setId(2L);
        TeacherAssignment existing = TeacherAssignment.builder()
            .teacher(other).subject(subject(10L, "DSA", true)).batch(batch).build();
        existing.setId(99L);
        when(repo.findByBatchId(5L)).thenReturn(List.of(existing));

        TeacherAssignmentRequest req = new TeacherAssignmentRequest(1L, 10L, 5L, null, null, null, null);

        assertThrows(ResourceConflictException.class, () -> service.create(req));
    }

    @Test
    void createAllowsSameSubjectDifferentTeachersAcrossSections() {
        when(teacherRepo.findById(1L)).thenReturn(Optional.of(qualifiedTeacher()));
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject(10L, "DSA", true)));
        Batch batch = batch(5L);
        when(sectionRepo.findById(7L)).thenReturn(Optional.of(section(7L, batch)));

        // Section A already has the subject allotted to another teacher; a
        // distinct section may still have its own (different) allotment.
        Teacher other = Teacher.builder().name("Dr. Ravi").build();
        other.setId(2L);
        TeacherAssignment existing = TeacherAssignment.builder()
            .teacher(other).subject(subject(10L, "DSA", true)).section(section(8L, batch)).build();
        existing.setId(99L);
        when(repo.findBySectionIdIn(List.of(7L))).thenReturn(List.of());

        when(repo.save(any(TeacherAssignment.class))).thenAnswer(inv -> {
            TeacherAssignment a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        TeacherAssignmentRequest req = new TeacherAssignmentRequest(1L, 10L, null, 7L, 4, 1, null);
        TeacherAssignmentResponse response = service.create(req);

        assertEquals(100L, response.id());
        assertEquals("CSE-2A-A", response.sectionLabel());
        verify(repo).save(any(TeacherAssignment.class));
    }

    @Test
    void createRejectsSubjectWithoutTeacher() {
        when(teacherRepo.findById(1L)).thenReturn(Optional.of(qualifiedTeacher()));
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject(10L, "Self Study", false)));

        TeacherAssignmentRequest req = new TeacherAssignmentRequest(1L, 10L, 5L, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void createSucceedsWithBatchScope() {
        when(teacherRepo.findById(1L)).thenReturn(Optional.of(qualifiedTeacher()));
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject(10L, "DSA", true)));
        Batch batch = batch(5L);
        when(batchRepo.findById(5L)).thenReturn(Optional.of(batch));
        when(repo.findByBatchId(5L)).thenReturn(List.of());
        when(repo.save(any(TeacherAssignment.class))).thenAnswer(inv -> {
            TeacherAssignment a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        TeacherAssignmentRequest req = new TeacherAssignmentRequest(1L, 10L, 5L, null, 4, 2, "Term 1");
        TeacherAssignmentResponse response = service.create(req);

        assertEquals(100L, response.id());
        assertEquals(1L, response.teacherId());
        assertEquals("CSE-2A", response.batchLabel());
        assertEquals(4, response.weeklyHours());
        assertEquals(2, response.priority());
        assertTrue(response.notes().contains("Term 1"));
    }
}