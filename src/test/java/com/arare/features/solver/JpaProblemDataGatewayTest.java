package com.arare.features.solver;

import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.BatchRepository;
import com.arare.features.building.BuildingRepository;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.preallocation.PreAllocationRepository;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.universityconfig.UniversityConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaProblemDataGatewayTest {

    @Mock
    private TimeslotRepository timeslotRepo;
    @Mock
    private BuildingRepository buildingRepo;
    @Mock
    private UniversityConfigRepository configRepo;
    @Mock
    private RoomRepository roomRepo;
    @Mock
    private TeacherRepository teacherRepo;
    @Mock
    private SubjectRepository subjectRepo;
    @Mock
    private BatchRepository batchRepo;
    @Mock
    private ClassSectionRepository sectionRepo;
    @Mock
    private ClassSessionRepository sessionRepo;
    @Mock
    private PreAllocationRepository preAllocationRepo;

    @InjectMocks
    private JpaProblemDataGateway gateway;

    private ProblemBuildRequest request(List<Long> batchIds, List<Long> teacherIds, List<Long> roomIds) {
        return new ProblemBuildRequest(null, null, null, null, batchIds, teacherIds, roomIds, null);
    }

    @Test
    void rejectsUnknownRoomIdsInsteadOfBuildingPartialProblem() {
        Room room = new Room();
        room.setId(1L);
        when(roomRepo.findAllById(List.of(1L, 99L))).thenReturn(List.of(room));

        assertThrows(IllegalArgumentException.class,
            () -> gateway.loadFacts(request(null, null, List.of(1L, 99L))));
    }

    @Test
    void rejectsUnknownTeacherIds() {
        when(roomRepo.findAll()).thenReturn(List.of());
        Teacher teacher = new Teacher();
        teacher.setId(2L);
        when(teacherRepo.findAllById(List.of(2L, 98L))).thenReturn(List.of(teacher));

        assertThrows(IllegalArgumentException.class,
            () -> gateway.loadFacts(request(null, List.of(2L, 98L), null)));
    }

    @Test
    void rejectsUnknownBatchIds() {
        when(roomRepo.findAll()).thenReturn(List.of());
        when(teacherRepo.findAll()).thenReturn(List.of());
        when(batchRepo.findAll()).thenReturn(List.of());
        when(batchRepo.findAllById(List.of(3L, 97L))).thenReturn(java.util.Collections.emptyList());

        assertThrows(IllegalArgumentException.class,
            () -> gateway.loadFacts(request(List.of(3L, 97L), null, null)));
    }

    @Test
    void resolvesFactsWhenAllIdsExist() {
        Room room = new Room();
        room.setId(1L);
        Teacher teacher = new Teacher();
        teacher.setId(2L);
        com.arare.features.batch.Batch batch = com.arare.features.batch.Batch.builder().build();
        batch.setId(3L);
        when(roomRepo.findAllById(List.of(1L))).thenReturn(List.of(room));
        when(teacherRepo.findAllById(List.of(2L))).thenReturn(List.of(teacher));
        when(timeslotRepo.findByType(TimeslotType.CLASS)).thenReturn(List.of());
        when(buildingRepo.findAll()).thenReturn(List.of());
        when(configRepo.findFirstByActiveTrue()).thenReturn(Optional.empty());
        when(subjectRepo.findAll()).thenReturn(List.of());
        when(batchRepo.findAll()).thenReturn(List.of(batch));
        when(batchRepo.findAllById(List.of(3L))).thenReturn(List.of(batch));
        when(sectionRepo.findByBatchIdIn(List.of(3L))).thenReturn(List.of());

        ProblemFacts facts = gateway.loadFacts(request(List.of(3L), List.of(2L), List.of(1L)));

        assertEquals(List.of(room), facts.rooms());
        assertEquals(List.of(teacher), facts.teachers());
        assertEquals(List.of(batch), facts.batches());
    }

    @Test
    void loadsAtMostOneActiveConfig() {
        when(roomRepo.findAll()).thenReturn(List.of());
        when(teacherRepo.findAll()).thenReturn(List.of());
        when(subjectRepo.findAll()).thenReturn(List.of());
        when(batchRepo.findAll()).thenReturn(List.of());
        when(timeslotRepo.findByType(TimeslotType.CLASS)).thenReturn(List.of());
        when(buildingRepo.findAll()).thenReturn(List.of());
        com.arare.features.universityconfig.UniversityConfig cfg =
            com.arare.features.universityconfig.UniversityConfig.builder().build();
        cfg.setId(1L);
        when(configRepo.findFirstByActiveTrue()).thenReturn(Optional.of(cfg));

        ProblemFacts facts = gateway.loadFacts(request(null, null, null));

        assertEquals(List.of(cfg), facts.configs());
    }
}
