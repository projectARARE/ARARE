package com.arare.features.dataimport;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export service producing the same flat CSV formats that the importers
 * consume. Both a full relational ZIP and single-entity CSV files are
 * available, so a ZIP export can be edited file-by-file and re-imported.
 *
 * <p>All files are written with a UTF-8 BOM (Excel-friendly) and sorted
 * deterministically by natural key so that repeated exports diff cleanly.
 */
@Service
@RequiredArgsConstructor
public class RelationalCsvExportService {

    private final TimeslotRepository timeslotRepository;
    private final BuildingRepository buildingRepository;
    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;
    private final UniversityConfigRepository configRepository;

    @Transactional(readOnly = true)
    public byte[] exportZip() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            for (CsvEntityType entityType : CsvEntityType.importOrder()) {
                writeEntry(zos, entityType.getFileName(), exportCsv(entityType));
            }
            writeEntry(zos, "dept_buildings.csv", exportDeptBuildings());
            writeEntry(zos, "teacher_subjects.csv", exportTeacherSubjects());
            writeEntry(zos, "teacher_availability.csv", exportTeacherAvailability());
            writeEntry(zos, "teacher_preferred_buildings.csv", exportTeacherPreferredBuildings());
            writeEntry(zos, "room_availability.csv", exportRoomAvailability());
            writeEntry(zos, "batch_working_days.csv", exportBatchWorkingDays());
            writeEntry(zos, "config_working_days.csv", exportConfigWorkingDays());
            writeEntry(zos, "config_break_indices.csv", exportConfigBreakIndices());

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export ZIP archive", e);
        }
    }

    /** Exports a single entity type in the flat import format (with BOM). */
    @Transactional(readOnly = true)
    public String exportCsv(CsvEntityType entityType) {
        return switch (entityType) {
            case TIMESLOTS -> exportTimeslots();
            case BUILDINGS -> exportBuildings();
            case DEPARTMENTS -> exportDepartments();
            case ROOMS -> exportRooms();
            case SUBJECTS -> exportSubjects();
            case TEACHERS -> exportTeachers();
            case BATCHES -> exportBatches();
        };
    }

    // =========================================================================
    // ZIP plumbing
    // =========================================================================

    private void writeEntry(ZipOutputStream zos, String filename, String content) throws IOException {
        if (content == null || content.isEmpty()) return;
        zos.putNextEntry(new ZipEntry(filename));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    // =========================================================================
    // Entity exports (sorted by natural key)
    // =========================================================================

    private String exportTimeslots() {
        List<Timeslot> all = timeslotRepository.findAll().stream()
            .sorted(Comparator.comparing(Timeslot::getDay)
                .thenComparing(Timeslot::getStartTime))
            .toList();
        List<List<String>> rows = all.stream()
            .map(t -> List.of(t.getDay().name(), t.getStartTime().toString(), t.getEndTime().toString(),
                t.getSlotNumber() == null ? "" : String.valueOf(t.getSlotNumber()), t.getType().name()))
            .toList();
        return CsvUtils.write(new String[]{"day", "startTime", "endTime", "slotNumber", "type"}, rows);
    }

    private String exportBuildings() {
        List<Building> all = buildingRepository.findAll().stream()
            .sorted(Comparator.comparing(Building::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<List<String>> rows = all.stream()
            .map(b -> List.of(b.getName(), b.getLocation() == null ? "" : b.getLocation()))
            .toList();
        return CsvUtils.write(new String[]{"name", "location"}, rows);
    }

    private String exportDepartments() {
        List<Department> all = departmentRepository.findAll().stream()
            .sorted(Comparator.comparing(Department::getCode, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<List<String>> rows = all.stream()
            .map(d -> List.of(d.getCode(), d.getName()))
            .toList();
        return CsvUtils.write(new String[]{"code", "name"}, rows);
    }

    private String exportRooms() {
        List<Room> all = roomRepository.findAll().stream()
            .sorted(Comparator.comparing((Room r) -> r.getBuilding() == null ? "" : r.getBuilding().getName())
                .thenComparing(Room::getRoomNumber, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<List<String>> rows = all.stream()
            .map(r -> List.of(
                r.getBuilding() == null ? "" : r.getBuilding().getName(),
                r.getRoomNumber(), r.getType().name(),
                r.getLabSubtype() == null ? "" : r.getLabSubtype().name(),
                String.valueOf(r.getCapacity())))
            .toList();
        return CsvUtils.write(
            new String[]{"buildingName", "roomNumber", "type", "labSubtype", "capacity"}, rows);
    }

    private String exportSubjects() {
        List<Subject> all = subjectRepository.findAll().stream()
            .sorted(Comparator.comparing((Subject s) -> s.getDepartment() == null ? "" : s.getDepartment().getCode())
                .thenComparing(s -> s.getCode() == null ? "" : s.getCode(), String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<List<String>> rows = all.stream()
            .map(s -> List.of(
                s.getDepartment() == null ? "" : s.getDepartment().getCode(),
                s.getCode() == null ? "" : s.getCode(), s.getName(),
                String.valueOf(s.getWeeklyHours()), String.valueOf(s.getChunkHours()),
                s.getRoomTypeRequired().name(),
                s.getLabSubtypeRequired() == null ? "" : s.getLabSubtypeRequired().name(),
                String.valueOf(s.isLab()), String.valueOf(s.isRequiresTeacher()),
                String.valueOf(s.isRequiresRoom()),
                String.valueOf(s.getMinGapBetweenSessions()),
                String.valueOf(s.getMaxSessionsPerDay())))
            .toList();
        return CsvUtils.write(new String[]{"departmentCode", "code", "name", "weeklyHours", "chunkHours",
            "roomTypeRequired", "labSubtypeRequired", "isLab", "requiresTeacher", "requiresRoom",
            "minGapBetweenSessions", "maxSessionsPerDay"}, rows);
    }

    private String exportTeachers() {
        List<Teacher> all = teacherRepository.findAll().stream()
            .sorted(Comparator.comparing(t -> t.getEmployeeId() == null ? "" : t.getEmployeeId()))
            .toList();
        List<List<String>> rows = all.stream()
            .map(t -> List.of(
                t.getEmployeeId() == null ? "" : t.getEmployeeId(), t.getName(),
                String.valueOf(t.getMaxDailyHours()), String.valueOf(t.getMaxWeeklyHours()),
                String.valueOf(t.getMaxConsecutiveClasses()), String.valueOf(t.getMovementPenalty()),
                t.getPreferredFreeDay() == null ? "" : t.getPreferredFreeDay().name()))
            .toList();
        return CsvUtils.write(new String[]{"employeeId", "name", "maxDailyHours", "maxWeeklyHours",
            "maxConsecutiveClasses", "movementPenalty", "preferredFreeDay"}, rows);
    }

    private String exportBatches() {
        List<Batch> all = batchRepository.findAll().stream()
            .sorted(Comparator.comparing((Batch b) -> b.getDepartment() == null ? "" : b.getDepartment().getCode())
                .thenComparingInt(Batch::getYear)
                .thenComparing(b -> b.getSection() == null ? "" : b.getSection(), String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<List<String>> rows = all.stream()
            .map(b -> List.of(
                b.getDepartment() == null ? "" : b.getDepartment().getCode(),
                String.valueOf(b.getYear()), b.getSection(),
                String.valueOf(b.getStudentCount()),
                b.getPreferredFreeDay() == null ? "" : b.getPreferredFreeDay().name()))
            .toList();
        return CsvUtils.write(new String[]{"departmentCode", "year", "section", "studentCount", "preferredFreeDay"}, rows);
    }

    // =========================================================================
    // Relationship exports (replace-per-mentioned-entity semantics)
    // =========================================================================

    private String exportDeptBuildings() {
        List<List<String>> rows = new ArrayList<>();
        for (Department d : departmentRepository.findAll().stream()
            .sorted(Comparator.comparing(Department::getCode, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (d.getBuildingsAllowed() == null) continue;
            for (Building b : d.getBuildingsAllowed()) {
                rows.add(List.of(d.getCode(), b.getName()));
            }
        }
        return CsvUtils.write(new String[]{"departmentCode", "buildingName"}, rows);
    }

    private String exportTeacherSubjects() {
        List<List<String>> rows = new ArrayList<>();
        for (Teacher t : teacherRepository.findAll().stream()
            .sorted(Comparator.comparing(t -> t.getEmployeeId() == null ? "" : t.getEmployeeId())).toList()) {
            if (t.getSubjects() == null || t.getEmployeeId() == null) continue;
            for (Subject s : t.getSubjects()) {
                if (s.getDepartment() == null || s.getCode() == null) continue;
                rows.add(List.of(t.getEmployeeId(), s.getDepartment().getCode(), s.getCode()));
            }
        }
        return CsvUtils.write(new String[]{"employeeId", "departmentCode", "subjectCode"}, rows);
    }

    private String exportTeacherAvailability() {
        List<List<String>> rows = new ArrayList<>();
        for (Teacher t : teacherRepository.findAll().stream()
            .sorted(Comparator.comparing(t -> t.getEmployeeId() == null ? "" : t.getEmployeeId())).toList()) {
            if (t.getAvailableTimeslots() == null || t.getEmployeeId() == null) continue;
            for (Timeslot ts : t.getAvailableTimeslots()) {
                rows.add(List.of(t.getEmployeeId(), ts.getDay().name(),
                    ts.getStartTime().toString(), ts.getEndTime().toString()));
            }
        }
        return CsvUtils.write(new String[]{"employeeId", "day", "startTime", "endTime"}, rows);
    }

    private String exportTeacherPreferredBuildings() {
        List<List<String>> rows = new ArrayList<>();
        for (Teacher t : teacherRepository.findAll().stream()
            .sorted(Comparator.comparing(t -> t.getEmployeeId() == null ? "" : t.getEmployeeId())).toList()) {
            if (t.getPreferredBuildings() == null || t.getEmployeeId() == null) continue;
            for (Building b : t.getPreferredBuildings()) {
                rows.add(List.of(t.getEmployeeId(), b.getName()));
            }
        }
        return CsvUtils.write(new String[]{"employeeId", "buildingName"}, rows);
    }

    private String exportRoomAvailability() {
        List<List<String>> rows = new ArrayList<>();
        for (Room r : roomRepository.findAll().stream()
            .sorted(Comparator.comparing((Room room) -> room.getBuilding() == null ? "" : room.getBuilding().getName())
                .thenComparing(Room::getRoomNumber, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (r.getAvailableTimeslots() == null || r.getBuilding() == null) continue;
            for (Timeslot ts : r.getAvailableTimeslots()) {
                rows.add(List.of(r.getBuilding().getName(), r.getRoomNumber(), ts.getDay().name(),
                    ts.getStartTime().toString(), ts.getEndTime().toString()));
            }
        }
        return CsvUtils.write(new String[]{"buildingName", "roomNumber", "day", "startTime", "endTime"}, rows);
    }

    private String exportBatchWorkingDays() {
        List<List<String>> rows = new ArrayList<>();
        for (Batch b : batchRepository.findAll().stream()
            .sorted(Comparator.comparing((Batch batch) -> batch.getDepartment() == null ? "" : batch.getDepartment().getCode())
                .thenComparingInt(Batch::getYear)
                .thenComparing(batch -> batch.getSection() == null ? "" : batch.getSection(), String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (b.getWorkingDays() == null || b.getDepartment() == null) continue;
            for (com.arare.common.enums.SchoolDay day : b.getWorkingDays()) {
                rows.add(List.of(b.getDepartment().getCode(), String.valueOf(b.getYear()), b.getSection(), day.name()));
            }
        }
        return CsvUtils.write(new String[]{"departmentCode", "year", "section", "day"}, rows);
    }

    private String exportConfigWorkingDays() {
        UniversityConfig config = activeConfig();
        if (config == null || config.getWorkingDays() == null) return "";
        List<List<String>> rows = config.getWorkingDays().stream().map(d -> List.of(d.name())).toList();
        return CsvUtils.write(new String[]{"day"}, rows);
    }

    private String exportConfigBreakIndices() {
        UniversityConfig config = activeConfig();
        if (config == null || config.getBreakSlotIndices() == null) return "";
        List<List<String>> rows = config.getBreakSlotIndices().stream().map(i -> List.of(String.valueOf(i))).toList();
        return CsvUtils.write(new String[]{"index"}, rows);
    }

    private UniversityConfig activeConfig() {
        return configRepository.findAll().stream().filter(UniversityConfig::isActive).findFirst().orElse(null);
    }
}