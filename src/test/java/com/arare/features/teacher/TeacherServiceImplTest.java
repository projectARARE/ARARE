package com.arare.features.teacher;

import com.arare.features.building.BuildingRepository;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.institute.Institute;
import com.arare.features.institute.InstituteRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherServiceImplTest {

    @Mock
    private TeacherRepository repo;

    @Mock
    private SubjectRepository subjectRepo;

    @Mock
    private TimeslotRepository timeslotRepo;

    @Mock
    private BuildingRepository buildingRepo;

    @Mock
    private ClassSessionRepository sessionRepo;

    @Mock
    private CascadeDeletionService cascadeDeletionService;

    @Mock
    private InstituteRepository instituteRepo;

    @InjectMocks
    private TeacherServiceImpl service;

    private TeacherRequest request(List<Long> subjectIds) {
        return new TeacherRequest(
            "EMP001", "Dr. Test", subjectIds, null, null,
            6, 20, 3, 1, null, null
        );
    }

    @Test
    void updateSyncsSubjectsAsManagedCollection() {
        Teacher teacher = Teacher.builder().name("Dr. Test").build();
        teacher.setId(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(teacher));

        Subject s1 = Subject.builder().name("DSA").build();
        s1.setId(10L);
        Subject s2 = Subject.builder().name("OS").build();
        s2.setId(11L);
        List<Subject> resolved = List.of(s1, s2);
        when(subjectRepo.findAllById(List.of(10L, 11L))).thenReturn(resolved);
        when(instituteRepo.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(repo.save(teacher)).thenReturn(teacher);

        TeacherResponse response = service.update(1L, request(List.of(10L, 11L)));

        assertSame(resolved, teacher.getSubjects());
        assertEquals(List.of(10L, 11L), response.subjectIds());
        verify(repo).save(teacher);
    }

    @Test
    void updateRejectsUnknownSubjectIdsInsteadOfSilentlyDropping() {
        Teacher teacher = Teacher.builder().name("Dr. Test").build();
        teacher.setId(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(teacher));

        Subject s1 = Subject.builder().name("DSA").build();
        s1.setId(10L);
        when(subjectRepo.findAllById(List.of(10L, 99L))).thenReturn(List.of(s1));

        assertThrows(IllegalArgumentException.class,
            () -> service.update(1L, request(List.of(10L, 99L))));
    }

    @Test
    void createRejectsUnknownTimeslotIds() {
        Timeslot slot = new Timeslot();
        slot.setId(5L);
        when(timeslotRepo.findAllById(List.of(5L, 88L))).thenReturn(List.of(slot));

        TeacherRequest req = new TeacherRequest(
            "EMP001", "Dr. Test", null, List.of(5L, 88L), null,
            6, 20, 3, 1, null, null
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void deletePurgesPreAllocationsAndEventLinks() {
        Teacher teacher = Teacher.builder().name("Dr. Test").build();
        teacher.setId(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(teacher));

        service.delete(1L);

        verify(sessionRepo).clearTeacherById(1L);
        verify(cascadeDeletionService).purgePreAllocationsForTeacher(1L);
        verify(cascadeDeletionService).detachTeacherFromEvents(1L);
        verify(repo).deleteById(1L);
    }

    @Test
    void createWithExplicitInstituteIdPersistsIt() {
        Institute inst = Institute.builder().name("Engg").code("E").build();
        inst.setId(7L);
        when(instituteRepo.findById(7L)).thenReturn(Optional.of(inst));
        Teacher saved = Teacher.builder().name("Dr. Test").institute(inst).build();
        saved.setId(1L);
        when(repo.save(any())).thenReturn(saved);

        TeacherRequest req = new TeacherRequest(
            "EMP001", "Dr. Test", null, null, null,
            6, 20, 3, 1, null, 7L
        );

        TeacherResponse response = service.create(req);

        assertEquals(7L, response.instituteId());
        assertEquals(inst, saved.getInstitute());
    }

    @Test
    void createWithNullInstituteFallsBackToSingleInstitute() {
        Institute inst = Institute.builder().name("Engg").code("E").build();
        inst.setId(7L);
        when(instituteRepo.findAllByOrderByNameAsc()).thenReturn(List.of(inst));
        Teacher saved = Teacher.builder().name("Dr. Test").institute(inst).build();
        saved.setId(1L);
        when(repo.save(any())).thenReturn(saved);

        TeacherRequest req = new TeacherRequest(
            "EMP001", "Dr. Test", null, null, null,
            6, 20, 3, 1, null, null
        );

        TeacherResponse response = service.create(req);

        assertEquals(7L, response.instituteId());
    }

    @Test
    void createWithNullInstituteAndMultipleInstitutesStaysGlobal() {
        Institute a = Institute.builder().name("A").code("A").build();
        a.setId(1L);
        Institute b = Institute.builder().name("B").code("B").build();
        b.setId(2L);
        when(instituteRepo.findAllByOrderByNameAsc()).thenReturn(List.of(a, b));
        Teacher saved = Teacher.builder().name("Dr. Test").build();
        saved.setId(1L);
        when(repo.save(any())).thenReturn(saved);

        TeacherRequest req = new TeacherRequest(
            "EMP001", "Dr. Test", null, null, null,
            6, 20, 3, 1, null, null
        );

        TeacherResponse response = service.create(req);

        assertNull(response.instituteId());
        assertNull(saved.getInstitute());
    }

    @Test
    void updateWithBlankNullInstituteKeepsExistingInstitute() {
        Institute inst = Institute.builder().name("Engg").code("E").build();
        inst.setId(7L);
        Teacher teacher = Teacher.builder().name("Dr. Test").institute(inst).build();
        teacher.setId(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(teacher));
        when(repo.save(teacher)).thenReturn(teacher);

        TeacherResponse response = service.update(1L, request(null));

        assertNotNull(response.instituteId());
        assertEquals(7L, response.instituteId());
    }
}
