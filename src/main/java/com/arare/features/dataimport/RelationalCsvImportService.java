package com.arare.features.dataimport;

import com.arare.common.enums.SchoolDay;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.building.Building;
import com.arare.features.building.BuildingRepository;
import com.arare.features.department.Department;
import com.arare.features.department.DepartmentRepository;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.universityconfig.UniversityConfig;
import com.arare.features.universityconfig.UniversityConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.arare.features.dataimport.CsvUtils.key;

/**
 * Relational ZIP importer used by the {@code POST /import/zip} endpoint.
 *
 * <p>The ZIP may contain the entity CSVs (flat format, relationships embedded)
 * and/or the normalized relationship CSVs ({@code *_*.csv} pairing files).
 * Entity rows are processed through the same shared {@link CsvEntityUpserter}
 * used by the single-entity importer, in dependency order; relationship files
 * then apply replace-per-mentioned-entity semantics.
 *
 * <p>Presence of a relationship CSV replaces that relationship for every
 * entity mentioned in it — entities not mentioned are left untouched
 * (partial update).
 */
@Service
@RequiredArgsConstructor
public class RelationalCsvImportService {

    /** Upper bound for an uploaded archive (50 MiB). */
    public static final long MAX_ZIP_BYTES = 50L * 1024L * 1024L;

    /** Upper bound for data rows in a single CSV inside an archive. */
    public static final int MAX_ROWS_PER_FILE = CsvUtils.MAX_DATA_ROWS_PER_FILE;

    /** Upper bound for the combined uncompressed CSV content of an archive. */
    public static final long MAX_UNCOMPRESSED_CHARS = MAX_ZIP_BYTES * 4;

    private final TimeslotRepository timeslotRepository;
    private final BuildingRepository buildingRepository;
    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;
    private final UniversityConfigRepository configRepository;
    private final CsvEntityUpserter upserter;

    @Transactional
    public CsvZipImportResponse importZip(MultipartFile file) {
        return importZip(file, false);
    }

    @Transactional
    public CsvZipImportResponse importZip(MultipartFile file, boolean dryRun) {
        Map<String, String> csvFiles = extractZip(file);
        Map<String, CsvZipImportResponse.FileImportStats> fileStats = new LinkedHashMap<>();
        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        // ── PASS 1: entity rows, in dependency order ─────────────────────────
        for (CsvEntityType entityType : CsvEntityType.importOrder()) {
            String csv = csvFiles.get(entityType.getFileName());
            if (csv == null || csv.isBlank()) continue;
            processEntityFile(csv, entityType, context, fileStats);
        }

        // ── PASS 2: relationship files (replace per mentioned entity) ───────
        processDeptBuildings(csvFiles.get("dept_buildings.csv"), context, fileStats);
        processTeacherSubjects(csvFiles.get("teacher_subjects.csv"), context, fileStats);
        processTeacherAvailability(csvFiles.get("teacher_availability.csv"), context, fileStats);
        processTeacherPreferredBuildings(csvFiles.get("teacher_preferred_buildings.csv"), context, fileStats);
        processRoomAvailability(csvFiles.get("room_availability.csv"), context, fileStats);
        processBatchWorkingDays(csvFiles.get("batch_working_days.csv"), context, fileStats);
        processConfigWorkingDays(csvFiles.get("config_working_days.csv"), fileStats);
        processConfigBreakIndices(csvFiles.get("config_break_indices.csv"), fileStats);

        markRollbackIfDryRun(dryRun);
        return new CsvZipImportResponse(fileStats, List.of(), dryRun);
    }

    // =========================================================================
    // ZIP extraction
    // =========================================================================

    Map<String, String> extractZip(MultipartFile file) {
        if (file.getSize() > MAX_ZIP_BYTES) {
            throw new IllegalArgumentException(
                "ZIP file exceeds the maximum size of " + (MAX_ZIP_BYTES / (1024 * 1024)) + " MiB");
        }
        Map<String, String> files = new HashMap<>();
        long totalChars = 0;
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String filename = entry.getName();
                if (filename.contains("/")) filename = filename.substring(filename.lastIndexOf('/') + 1);
                filename = filename.toLowerCase(Locale.ROOT);
                if (filename.endsWith(".csv")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    if (!content.isEmpty()) {
                        totalChars += content.length();
                        if (totalChars > MAX_UNCOMPRESSED_CHARS) {
                            throw new IllegalArgumentException(
                                "ZIP archive exceeds the maximum combined CSV size");
                        }
                        files.put(filename, content);
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read ZIP file: " + e.getMessage());
        }
        return files;
    }

    private void processEntityFile(String csv, CsvEntityType entityType,
                                   ImportContext context,
                                   Map<String, CsvZipImportResponse.FileImportStats> fileStats) {
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        int created = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                if (upserter.upsert(entityType, rows.get(i), i + 2, context)) created++;
                else updated++;
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }
        fileStats.put(entityType.getFileName(),
            new CsvZipImportResponse.FileImportStats(entityType.getFileName(), created, updated, skipped, errors));
    }

    // =========================================================================
    // PASS 2 — relationship processors (replace per mentioned entity)
    // =========================================================================

    private void processDeptBuildings(String csv, ImportContext context,
                                      Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        Map<Department, List<Building>> grouped = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, String> row = rows.get(i);
                int rn = i + 2;
                Department d = require(context.departmentByCode(key(CsvUtils.required(row, "departmentcode", rn))),
                    "department not found for row " + rn);
                Building b = require(context.buildingByName(CsvUtils.required(row, "buildingname", rn)),
                    "building not found for row " + rn);
                grouped.computeIfAbsent(d, k -> new ArrayList<>()).add(b);
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }

        int written = 0;
        for (Map.Entry<Department, List<Building>> entry : grouped.entrySet()) {
            entry.getKey().setBuildingsAllowed(new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            departmentRepository.save(entry.getKey());
            written += entry.getValue().size();
        }
        stats.put("dept_buildings.csv",
            new CsvZipImportResponse.FileImportStats("dept_buildings.csv", written, 0, skipped, errors));
    }

    private void processTeacherSubjects(String csv, ImportContext context,
                                        Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        Map<Teacher, List<Subject>> grouped = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, String> row = rows.get(i);
                int rn = i + 2;
                Teacher t = require(context.teacherByEmployeeId(CsvUtils.required(row, "employeeid", rn)),
                    "teacher not found for row " + rn);
                Subject s = require(
                    context.subject(CsvUtils.required(row, "departmentcode", rn), CsvUtils.required(row, "subjectcode", rn)),
                    "subject not found for row " + rn);
                grouped.computeIfAbsent(t, k -> new ArrayList<>()).add(s);
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }

        int written = 0;
        for (Map.Entry<Teacher, List<Subject>> entry : grouped.entrySet()) {
            entry.getKey().setSubjects(new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            teacherRepository.save(entry.getKey());
            written += entry.getValue().size();
        }
        stats.put("teacher_subjects.csv",
            new CsvZipImportResponse.FileImportStats("teacher_subjects.csv", written, 0, skipped, errors));
    }

    private void processTeacherAvailability(String csv, ImportContext context,
                                            Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        Map<Teacher, List<Timeslot>> grouped = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, String> row = rows.get(i);
                int rn = i + 2;
                Teacher t = require(context.teacherByEmployeeId(CsvUtils.required(row, "employeeid", rn)),
                    "teacher not found for row " + rn);
                Timeslot ts = require(context.timeslot(
                        CsvUtils.required(row, "day", rn),
                        CsvUtils.required(row, "starttime", rn),
                        CsvUtils.required(row, "endtime", rn)),
                    "timeslot not found for row " + rn);
                grouped.computeIfAbsent(t, k -> new ArrayList<>()).add(ts);
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }

        int written = 0;
        for (Map.Entry<Teacher, List<Timeslot>> entry : grouped.entrySet()) {
            entry.getKey().setAvailableTimeslots(new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            teacherRepository.save(entry.getKey());
            written += entry.getValue().size();
        }
        stats.put("teacher_availability.csv",
            new CsvZipImportResponse.FileImportStats("teacher_availability.csv", written, 0, skipped, errors));
    }

    private void processTeacherPreferredBuildings(String csv, ImportContext context,
                                                  Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        Map<Teacher, List<Building>> grouped = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, String> row = rows.get(i);
                int rn = i + 2;
                Teacher t = require(context.teacherByEmployeeId(CsvUtils.required(row, "employeeid", rn)),
                    "teacher not found for row " + rn);
                Building b = require(context.buildingByName(CsvUtils.required(row, "buildingname", rn)),
                    "building not found for row " + rn);
                grouped.computeIfAbsent(t, k -> new ArrayList<>()).add(b);
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }

        int written = 0;
        for (Map.Entry<Teacher, List<Building>> entry : grouped.entrySet()) {
            entry.getKey().setPreferredBuildings(new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            teacherRepository.save(entry.getKey());
            written += entry.getValue().size();
        }
        stats.put("teacher_preferred_buildings.csv",
            new CsvZipImportResponse.FileImportStats("teacher_preferred_buildings.csv", written, 0, skipped, errors));
    }

    private void processRoomAvailability(String csv, ImportContext context,
                                         Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        Map<Room, List<Timeslot>> grouped = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, String> row = rows.get(i);
                int rn = i + 2;
                Room r = require(context.room(
                        CsvUtils.required(row, "buildingname", rn),
                        CsvUtils.required(row, "roomnumber", rn)),
                    "room not found for row " + rn);
                Timeslot ts = require(context.timeslot(
                        CsvUtils.required(row, "day", rn),
                        CsvUtils.required(row, "starttime", rn),
                        CsvUtils.required(row, "endtime", rn)),
                    "timeslot not found for row " + rn);
                grouped.computeIfAbsent(r, k -> new ArrayList<>()).add(ts);
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }

        int written = 0;
        for (Map.Entry<Room, List<Timeslot>> entry : grouped.entrySet()) {
            entry.getKey().setAvailableTimeslots(new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            roomRepository.save(entry.getKey());
            written += entry.getValue().size();
        }
        stats.put("room_availability.csv",
            new CsvZipImportResponse.FileImportStats("room_availability.csv", written, 0, skipped, errors));
    }

    private void processBatchWorkingDays(String csv, ImportContext context,
                                         Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        Map<Batch, List<SchoolDay>> grouped = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, String> row = rows.get(i);
                int rn = i + 2;
                Batch b = require(context.batch(
                        CsvUtils.required(row, "departmentcode", rn),
                        Integer.parseInt(CsvUtils.required(row, "year", rn)),
                        CsvUtils.required(row, "section", rn)),
                    "batch not found for row " + rn);
                SchoolDay day = CsvUtils.parseEnum(SchoolDay.class, CsvUtils.required(row, "day", rn));
                grouped.computeIfAbsent(b, k -> new ArrayList<>()).add(day);
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }

        int written = 0;
        for (Map.Entry<Batch, List<SchoolDay>> entry : grouped.entrySet()) {
            entry.getKey().setWorkingDays(new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            batchRepository.save(entry.getKey());
            written += entry.getValue().size();
        }
        stats.put("batch_working_days.csv",
            new CsvZipImportResponse.FileImportStats("batch_working_days.csv", written, 0, skipped, errors));
    }

    private void processConfigWorkingDays(String csv, Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        UniversityConfig config = activeConfig();
        if (config == null || config.getWorkingDays() == null) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        List<SchoolDay> days = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                days.add(CsvUtils.parseEnum(SchoolDay.class, CsvUtils.required(rows.get(i), "day", i + 2)));
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }
        config.setWorkingDays(new ArrayList<>(new LinkedHashSet<>(days)));
        config.setDaysPerWeek(config.getWorkingDays().size());
        configRepository.save(config);
        stats.put("config_working_days.csv",
            new CsvZipImportResponse.FileImportStats("config_working_days.csv", days.size(), 0, skipped, errors));
    }

    private void processConfigBreakIndices(String csv, Map<String, CsvZipImportResponse.FileImportStats> stats) {
        if (csv == null || csv.isBlank()) return;
        UniversityConfig config = activeConfig();
        if (config == null || config.getBreakSlotIndices() == null) return;
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        List<Integer> indices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                indices.add(Integer.parseInt(CsvUtils.required(rows.get(i), "index", i + 2)));
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }
        config.setBreakSlotIndices(new ArrayList<>(new LinkedHashSet<>(indices)));
        configRepository.save(config);
        stats.put("config_break_indices.csv",
            new CsvZipImportResponse.FileImportStats("config_break_indices.csv", indices.size(), 0, skipped, errors));
    }

    private UniversityConfig activeConfig() {
        return configRepository.findAll().stream().filter(UniversityConfig::isActive).findFirst().orElse(null);
    }

    private static <T> T require(T entity, String message) {
        if (entity == null) throw new IllegalArgumentException(message);
        return entity;
    }

    /**
     * In dry-run mode nothing is written to the database: the enclosing
     * transaction is marked rollback-only while the response still reports
     * the changes that would have been applied.
     */
    private void markRollbackIfDryRun(boolean dryRun) {
        if (dryRun && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}