package com.arare.features.dataimport;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.BatchRepository;
import com.arare.features.building.BuildingRepository;
import com.arare.features.department.DepartmentRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock private TimeslotRepository timeslotRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private CsvEntityUpserter upserter;

    @InjectMocks
    private CsvImportService service;

    @BeforeEach
    void setUp() {
        lenient().when(timeslotRepository.findAll()).thenReturn(List.of());
        lenient().when(buildingRepository.findAll()).thenReturn(List.of());
        lenient().when(departmentRepository.findAll()).thenReturn(List.of());
        lenient().when(roomRepository.findAll()).thenReturn(List.of());
        lenient().when(subjectRepository.findAll()).thenReturn(List.of());
        lenient().when(teacherRepository.findAll()).thenReturn(List.of());
        lenient().when(batchRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void importsTimeslotsRegardlessOfEntityTypeCase() {
        when(upserter.upsert(any(), any(), anyInt(), any())).thenReturn(true);
        String csv = String.join("\n",
            "day,startTime,endTime,slotNumber,type",
            "MONDAY,09:00,10:00,1,CLASS"
        );

        CsvImportResponse response = service.importCsv("Timeslots", csv);

        assertEquals("timeslots", response.entityType());
        assertEquals(1, response.created());
        assertEquals(0, response.updated());
        assertEquals(0, response.skipped());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void stripsBomAndParsesHeaderColumnsCorrectly() {
        when(upserter.upsert(any(), any(), anyInt(), any())).thenReturn(true);
        String csv = String.join("\n",
            "\uFEFFday,startTime,endTime,slotNumber,type",
            "MONDAY,09:00,10:00,1,CLASS"
        );

        CsvImportResponse response = service.importCsv("timeslots", csv);

        assertEquals(1, response.created());
        assertEquals(0, response.skipped());
        assertTrue(response.errors().isEmpty());
        verify(upserter).upsert(any(), any(), anyInt(), any());
    }

    @Test
    void rejectsUnknownEntityType() {
        assertThrows(IllegalArgumentException.class,
            () -> service.importCsv("gadgets", "name\nX\n"));
    }

    @Test
    void rejectsImportWhenDependenciesAreMissing() {
        when(timeslotRepository.findAll()).thenReturn(
            List.of(timeslot(SchoolDay.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), TimeslotType.CLASS)));
        // buildings, departments, subjects are all empty â†’ teachers cannot be imported

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.importCsv("teachers", "employeeId,name\nEMP1,Dr. A\n"));

        assertTrue(ex.getMessage().contains("Buildings"));
        assertTrue(ex.getMessage().contains("Departments"));
        assertTrue(ex.getMessage().contains("Subjects"));
    }

    @Test
    void teacherImportIsAllowedWhenAllDependenciesExist() {
        when(timeslotRepository.findAll()).thenReturn(List.of(
            timeslot(SchoolDay.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), TimeslotType.CLASS)));
        com.arare.features.building.Building building =
            com.arare.features.building.Building.builder().name("Block A").build();
        building.setId(1L);
        when(buildingRepository.findAll()).thenReturn(List.of(building));
        com.arare.features.department.Department dept =
            com.arare.features.department.Department.builder().code("CSE").name("CS").build();
        dept.setId(1L);
        when(departmentRepository.findAll()).thenReturn(List.of(dept));
        com.arare.features.subject.Subject subject = new com.arare.features.subject.Subject();
        subject.setDepartment(dept);
        subject.setCode("CSE101");
        subject.setName("DS");
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(upserter.upsert(any(), any(), anyInt(), any())).thenReturn(false);

        CsvImportResponse response =
            service.importCsv("teachers", "employeeId,name\nEMP1,New Name\n");

        assertEquals(1, response.updated());
        assertEquals(0, response.skipped());
        verify(upserter).upsert(any(), any(), anyInt(), any());
    }

    @Test
    void blankCsvThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.importCsv("timeslots", "   \n"));
    }

    @Test
    void dryRunReturnsDryRunFlag() {
        when(upserter.upsert(any(), any(), anyInt(), any())).thenReturn(true);
        String csv = String.join("\n",
            "day,startTime,endTime,slotNumber,type",
            "MONDAY,09:00,10:00,1,CLASS"
        );

        CsvImportResponse response = service.importCsv("timeslots", csv, true);

        assertTrue(response.dryRun());
        assertEquals(1, response.created());
    }

    private static Timeslot timeslot(SchoolDay day, LocalTime start, LocalTime end, TimeslotType type) {
        Timeslot t = new Timeslot();
        t.setDay(day);
        t.setStartTime(start);
        t.setEndTime(end);
        t.setType(type);
        return t;
    }
}
