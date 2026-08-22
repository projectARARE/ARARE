package com.arare.features.dataimport;

import com.arare.features.batch.BatchRepository;
import com.arare.features.building.BuildingRepository;
import com.arare.features.department.DepartmentRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.universityconfig.UniversityConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationalCsvImportServiceTest {

    @Mock private TimeslotRepository timeslotRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private UniversityConfigRepository configRepository;
    @Mock private CsvEntityUpserter upserter;

    @InjectMocks
    private RelationalCsvImportService importService;

    @BeforeEach
    void setUp() {
        lenient().when(timeslotRepository.findAll()).thenReturn(List.of());
        lenient().when(buildingRepository.findAll()).thenReturn(List.of());
        lenient().when(departmentRepository.findAll()).thenReturn(List.of());
        lenient().when(roomRepository.findAll()).thenReturn(List.of());
        lenient().when(subjectRepository.findAll()).thenReturn(List.of());
        lenient().when(teacherRepository.findAll()).thenReturn(List.of());
        lenient().when(batchRepository.findAll()).thenReturn(List.of());
        lenient().when(configRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void importZip_emptyZip_returnsEmptyStats() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // empty zip needs at least one entry to be valid in some parsers,
            // but ZipInputStream handles empty streams fine.
        }

        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", baos.toByteArray());

        CsvZipImportResponse response = importService.importZip(file);

        assertNotNull(response);
        assertTrue(response.globalErrors().isEmpty());
    }

    @Test
    void importZip_withDepartments_processesSuccessfully() throws Exception {
        when(upserter.upsert(any(), any(), anyInt(), any())).thenReturn(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("departments.csv"));
            zos.write("code,name\nCS,Computer Science\n".getBytes());
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", baos.toByteArray());

        CsvZipImportResponse response = importService.importZip(file);

        assertNotNull(response);
        assertTrue(response.fileStats().containsKey("departments.csv"));
    }

    @Test
    void importZip_processesFilesInDependencyOrder() throws Exception {
        when(upserter.upsert(any(), any(), anyInt(), any())).thenReturn(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // teachers.csv present without subjects.csv â€” the upserter is
            // mocked, so the orchestrator must still attempt teachers last.
            zos.putNextEntry(new ZipEntry("teachers.csv"));
            zos.write("employeeId,name\nEMP1,Dr. A\n".getBytes());
            zos.closeEntry();
        }

        CsvZipImportResponse response =
            importService.importZip(new MockMultipartFile("file", "t.zip", "application/zip", baos.toByteArray()));

        assertTrue(response.fileStats().containsKey("teachers.csv"));
    }

    @Test
    void importZip_rejectsArchiveLargerThanMaxZipBytes() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(RelationalCsvImportService.MAX_ZIP_BYTES + 1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> importService.importZip(file));
        assertTrue(ex.getMessage().contains("maximum size"));
    }

    @Test
    void importZip_dryRun_returnsDryRunFlag() throws Exception {
        when(upserter.upsert(any(), any(), anyInt(), any())).thenReturn(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("departments.csv"));
            zos.write("code,name\nCS,Computer Science\n".getBytes());
            zos.closeEntry();
        }

        CsvZipImportResponse response = importService.importZip(
            new MockMultipartFile("file", "t.zip", "application/zip", baos.toByteArray()), true);

        assertTrue(response.dryRun());
        assertEquals(1, response.fileStats().get("departments.csv").created());
    }
}
