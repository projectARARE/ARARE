package com.arare.features.subjectoffering;

import com.arare.exception.ResourceConflictException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.department.Department;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectOfferingServiceImplTest {

    @Mock private SubjectOfferingRepository repo;
    @Mock private SubjectRepository subjectRepo;
    @Mock private BatchRepository batchRepo;
    @Mock private ClassSectionRepository sectionRepo;

    @InjectMocks private SubjectOfferingServiceImpl service;

    private Department dept;
    private Subject subject;
    private Batch batch;

    @BeforeEach
    void setUp() {
        dept = new Department();
        dept.setId(1L);
        dept.setCode("CSE");
        dept.setName("Computer Science");

        subject = Subject.builder()
            .name("DSA")
            .code("CS201")
            .department(dept)
            .weeklyHours(2)
            .chunkHours(1)
            .build();
        subject.setId(10L);

        batch = Batch.builder()
            .department(dept)
            .year(2)
            .section("A")
            .studentCount(60)
            .build();
        batch.setId(1L);
    }

    private ClassSection section() {
        ClassSection cs = ClassSection.builder().batch(batch).label("A").size(30).build();
        cs.setId(20L);
        return cs;
    }

    @Test
    void createRejectsMissingBothBatchAndSection() {
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(new SubjectOfferingRequest(10L, null, null, null, false)));
    }

    @Test
    void createRejectsBothBatchAndSection() {
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(new SubjectOfferingRequest(10L, 1L, 20L, null, false)));
    }

    @Test
    void createRejectsUnknownSubject() {
        when(subjectRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
            () -> service.create(new SubjectOfferingRequest(99L, 1L, null, null, false)));
    }

    @Test
    void createPersistsBatchOffering() {
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject));
        when(batchRepo.findById(1L)).thenReturn(Optional.of(batch));
        when(repo.findByBatchId(1L)).thenReturn(List.of());
        when(repo.save(any(SubjectOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectOfferingResponse resp = service.create(
            new SubjectOfferingRequest(10L, 1L, null, 4, true));

        assertEquals(10L, resp.subjectId());
        assertEquals("CSE-2A", resp.batchLabel());
        assertEquals(4, resp.weeklyHours());
        assertEquals(true, resp.elective());
        verify(repo).save(any(SubjectOffering.class));
    }

    @Test
    void createRejectsDuplicateForSameBatch() {
        SubjectOffering existing = SubjectOffering.builder()
            .subject(subject).batch(batch).build();
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject));
        when(batchRepo.findById(1L)).thenReturn(Optional.of(batch));
        when(repo.findByBatchId(1L)).thenReturn(List.of(existing));

        assertThrows(ResourceConflictException.class,
            () -> service.create(new SubjectOfferingRequest(10L, 1L, null, null, false)));
    }

    @Test
    void createInfersBatchFromSection() {
        ClassSection cs = section();
        when(subjectRepo.findById(10L)).thenReturn(Optional.of(subject));
        when(sectionRepo.findById(20L)).thenReturn(Optional.of(cs));
        when(repo.findBySectionId(20L)).thenReturn(List.of());
        when(repo.save(any(SubjectOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectOfferingResponse resp = service.create(
            new SubjectOfferingRequest(10L, null, 20L, null, false));

        assertEquals("CSE-2A-A", resp.sectionLabel());
        verify(repo).save(any(SubjectOffering.class));
    }

    @Test
    void deleteRemovesOffering() {
        SubjectOffering existing = SubjectOffering.builder()
            .subject(subject).batch(batch).build();
        existing.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(7L);
        verify(repo).deleteById(7L);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));
    }
}
