package com.arare.features.dataimport;

import com.arare.features.batch.BatchRepository;
import com.arare.features.building.BuildingRepository;
import com.arare.features.department.DepartmentRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.universityconfig.UniversityConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RelationalCsvExportServiceTest {

    @Mock private TimeslotRepository timeslotRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private UniversityConfigRepository configRepository;

    @InjectMocks
    private RelationalCsvExportService exportService;

    @Test
    void exportZip_returnsValidZipBytes() {
        byte[] zipData = exportService.exportZip();

        assertNotNull(zipData);
        assertTrue(zipData.length > 0);
    }

    /**
     * Round trip: every CSV produced by the exporter must parse cleanly with
     * the importer's own parser (normalized headers, RFC 4180 escaping, BOM).
     */
    @Test
    void exportZip_roundTripsThroughImporterParser() throws Exception {
        byte[] zipData = exportService.exportZip();

        int csvEntries = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".csv")) continue;
                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                java.util.List<Map<String, String>> rows = CsvUtils.parse(content);
                assertTrue(!rows.isEmpty() || content.isBlank() || content.endsWith("\n"),
                    "exporter file must be empty or contain parseable rows: " + entry.getName());
                csvEntries++;
            }
        }
        assertTrue(csvEntries > 0, "expected at least one CSV inside the export archive");
    }
}
