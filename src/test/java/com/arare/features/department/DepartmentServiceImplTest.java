package com.arare.features.department;

import com.arare.features.batch.BatchRepository;
import com.arare.features.building.Building;
import com.arare.features.building.BuildingRepository;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.institute.Institute;
import com.arare.features.institute.InstituteRepository;
import com.arare.features.subject.SubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock private DepartmentRepository repo;
    @Mock private InstituteRepository instituteRepo;
    @Mock private BuildingRepository buildingRepo;
    @Mock private ClassSessionRepository sessionRepo;
    @Mock private ClassSectionRepository sectionRepo;
    @Mock private BatchRepository batchRepo;
    @Mock private SubjectRepository subjectRepo;
    @Mock private CascadeDeletionService cascadeDeletionService;

    @InjectMocks
    private DepartmentServiceImpl service;

    @Test
    void updateKeepsBuildingsWhenBuildingIdsIsNull() {
        Building existing = Building.builder().name("Block A").build();
        existing.setId(1L);
        List<Building> current = new ArrayList<>(List.of(existing));

        Institute inst = new Institute();
        inst.setId(5L);

        Department dept = Department.builder()
            .name("CSE").code("CS").institute(inst).buildingsAllowed(current).build();
        dept.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(dept));
        when(instituteRepo.findById(5L)).thenReturn(Optional.of(inst));
        when(repo.save(dept)).thenReturn(dept);

        service.update(7L, new DepartmentRequest("CSE", "CS", 5L, null));

        assertSame(current, dept.getBuildingsAllowed());
        verify(buildingRepo, never()).findAllById(org.mockito.ArgumentMatchers.anyList());
        verify(repo).save(dept);
    }

    @Test
    void updateResolvesBuildingsWhenIdsProvided() {
        Building newBuilding = Building.builder().name("Block B").build();
        newBuilding.setId(2L);
        // findAllById must return the same count as ids requested so the size check passes.
        List<Building> resolved = List.of(newBuilding);

        Institute inst = new Institute();
        inst.setId(5L);

        Department dept = Department.builder().name("CSE").code("CS").institute(inst).build();
        dept.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(dept));
        when(instituteRepo.findById(5L)).thenReturn(Optional.of(inst));
        when(buildingRepo.findAllById(List.of(2L))).thenReturn(resolved);
        when(repo.save(dept)).thenReturn(dept);

        service.update(7L, new DepartmentRequest("CSE", "CS", 5L, List.of(2L)));

        assertSame(resolved, dept.getBuildingsAllowed());
    }

    // Regression: resolveBuildings() must reject unknown IDs with IllegalArgumentException (→ 400),
    // consistent with TeacherServiceImpl.resolveAll(). Previously it silently dropped the unknown ID.
    @Test
    void updateThrows400WhenBuildingIdDoesNotExist() {
        Institute inst = new Institute();
        inst.setId(5L);

        Department dept = Department.builder().name("CSE").code("CS").institute(inst).build();
        dept.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(dept));
        when(instituteRepo.findById(5L)).thenReturn(Optional.of(inst));
        // findAllById returns 0 records but 1 was requested → unknown ID.
        when(buildingRepo.findAllById(List.of(99L))).thenReturn(List.of());

        assertThrows(
            IllegalArgumentException.class,
            () -> service.update(7L, new DepartmentRequest("CSE", "CS", 5L, List.of(99L)))
        );
    }

    @Test
    void deletePurgesPreAllocations() {
        Department dept = Department.builder().name("CSE").code("CS").build();
        dept.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(dept));

        service.delete(7L);

        verify(cascadeDeletionService).purgePreAllocationsForDepartment(7L);
        verify(repo).deleteById(7L);
    }
}
