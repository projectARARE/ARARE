package com.arare.features.dataimport;

import com.arare.common.enums.SchoolDay;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock
    private TimeslotRepository timeslotRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private CsvImportService service;

    @BeforeEach
    void setUp() {
        when(timeslotRepository.findByDay(any(SchoolDay.class))).thenReturn(List.of());
        when(timeslotRepository.save(any(Timeslot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void importsTimeslotsRegardlessOfEntityTypeCase() {
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
        String csv = String.join("\n",
            "\uFEFFday,startTime,endTime,slotNumber,type",
            "MONDAY,09:00,10:00,1,CLASS"
        );

        CsvImportResponse response = service.importCsv("timeslots", csv);

        assertEquals(1, response.created());
        assertEquals(0, response.skipped());
        assertTrue(response.errors().isEmpty());

        ArgumentCaptor<Timeslot> captor = ArgumentCaptor.forClass(Timeslot.class);
        verify(timeslotRepository).save(captor.capture());
        assertEquals(SchoolDay.MONDAY, captor.getValue().getDay());
    }
}
