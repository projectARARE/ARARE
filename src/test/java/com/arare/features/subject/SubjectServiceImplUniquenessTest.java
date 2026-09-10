package com.arare.features.subject;

import com.arare.common.enums.RoomType;
import com.arare.exception.DuplicateResourceException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.department.Department;
import com.arare.features.department.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceImplUniquenessTest {

    @Mock private SubjectRepository repo;
    @Mock private DepartmentRepository departmentRepo;
    @Mock private ClassSessionRepository sessionRepo;
    @Mock private CascadeDeletionService cascadeDeletionService;

    @InjectMocks private SubjectServiceImpl service;

    private Department dept;

    @BeforeEach
    void setUp() {
        dept = new Department();
        dept.setId(1L);
        dept.setCode("CSE");
        dept.setName("Computer Science");
    }

    private SubjectRequest request(Long departmentId) {
        return new SubjectRequest(
            "DSA", "CS201", departmentId, 4, 1, RoomType.LECTURE,
            null, false, true, true, 0, 1);
    }

    @Test
    void createRejectsDuplicateCodeWithinDepartment() {
        when(departmentRepo.findById(1L)).thenReturn(Optional.of(dept));
        when(repo.existsByDepartmentIdAndCode(1L, "CS201")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.create(request(1L)));
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(Subject.class));
    }

    @Test
    void createAllowsSameCodeInDifferentDepartment() {
        Department it = new Department();
        it.setId(2L);
        it.setCode("IT");
        it.setName("Information Technology");
        when(departmentRepo.findById(2L)).thenReturn(Optional.of(it));
        when(repo.existsByDepartmentIdAndCode(2L, "CS201")).thenReturn(false);
        when(repo.save(org.mockito.ArgumentMatchers.any(Subject.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.create(request(2L));
        verify(repo).save(org.mockito.ArgumentMatchers.any(Subject.class));
    }

    @Test
    void createRejectsDuplicateInstituteWideCode() {
        when(repo.existsByCodeAndDepartmentIsNull("CS201")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.create(request(null)));
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(Subject.class));
    }

    @Test
    void createAllowsInstituteWideCodeWhenNoDuplicate() {
        when(repo.existsByCodeAndDepartmentIsNull("CS201")).thenReturn(false);
        when(repo.save(org.mockito.ArgumentMatchers.any(Subject.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.create(request(null));
        verify(repo).save(org.mockito.ArgumentMatchers.any(Subject.class));
    }

    @Test
    void createThrowsNotFoundForUnknownDepartment() {
        when(departmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(request(99L)));
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(Subject.class));
    }

    @Test
    void updateAllowsUnchangedCodeAndDepartment() {
        Subject existing = new Subject();
        existing.setId(5L);
        existing.setCode("CS201");
        existing.setDepartment(dept);
        when(departmentRepo.findById(1L)).thenReturn(Optional.of(dept));
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.existsByDepartmentIdAndCodeAndIdNot(1L, "CS201", 5L)).thenReturn(false);
        when(repo.save(org.mockito.ArgumentMatchers.any(Subject.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.update(5L, request(1L));
        verify(repo).save(org.mockito.ArgumentMatchers.any(Subject.class));
    }

    @Test
    void updateRejectsCodeTakenByAnotherSubjectInDepartment() {
        Subject existing = new Subject();
        existing.setId(5L);
        existing.setCode("CS199");
        existing.setDepartment(dept);
        when(departmentRepo.findById(1L)).thenReturn(Optional.of(dept));
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.existsByDepartmentIdAndCodeAndIdNot(1L, "CS201", 5L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.update(5L, request(1L)));
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(Subject.class));
    }
}