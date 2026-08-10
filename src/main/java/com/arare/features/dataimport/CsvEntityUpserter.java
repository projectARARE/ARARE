package com.arare.features.dataimport;

import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared per-row upsert logic for every CSV import path (single-entity and
 * relational ZIP). Each method either creates or updates one entity by its
 * natural key and records the outcome on the shared {@link ImportContext}.
 *
 * <p>Partial-update semantics: only the columns present in the row are
 * applied. Blank optional columns keep existing values on update and fall
 * back to defaults on create. Relationship tokens (e.g. {@code subjectCodes})
 * replace the current set when present and are left untouched when blank —
 * a CSV that omits them never wipes existing data.
 */
@Service
@RequiredArgsConstructor
public class CsvEntityUpserter {

    private final TimeslotRepository timeslotRepository;
    private final BuildingRepository buildingRepository;
    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;

    /**
     * Upserts one row for the given entity type.
     *
     * @return {@code true} when a new entity was created, {@code false} when an existing one was updated
     * @throws IllegalArgumentException with row context when the row is invalid
     */
    public boolean upsert(CsvEntityType type, Map<String, String> row, int rowNumber, ImportContext context) {
        return switch (type) {
            case TIMESLOTS -> upsertTimeslot(row, rowNumber, context);
            case BUILDINGS -> upsertBuilding(row, rowNumber, context);
            case DEPARTMENTS -> upsertDepartment(row, rowNumber, context);
            case ROOMS -> upsertRoom(row, rowNumber, context);
            case SUBJECTS -> upsertSubject(row, rowNumber, context);
            case TEACHERS -> upsertTeacher(row, rowNumber, context);
            case BATCHES -> upsertBatch(row, rowNumber, context);
        };
    }

    // =========================================================================
    // Entity upserts
    // =========================================================================

    private boolean upsertTimeslot(Map<String, String> row, int rowNumber, ImportContext context) {
        SchoolDay day = CsvUtils.parseEnum(SchoolDay.class, CsvUtils.required(row, "day", rowNumber));
        LocalTime start = LocalTime.parse(CsvUtils.required(row, "starttime", rowNumber));
        LocalTime end = LocalTime.parse(CsvUtils.required(row, "endtime", rowNumber));

        Timeslot entity = context.timeslot(day.name(), start.toString(), end.toString());
        boolean created = entity == null;
        if (created) entity = new Timeslot();

        entity.setDay(day);
        entity.setStartTime(start);
        entity.setEndTime(end);
        String slotNumber = CsvUtils.blankToNull(row.get("slotnumber"));
        if (slotNumber != null || created) entity.setSlotNumber(CsvUtils.optionalInt(slotNumber));
        String type = CsvUtils.blankToNull(row.get("type"));
        if (type != null || created) {
            entity.setType(CsvUtils.parseEnumOrDefault(TimeslotType.class, type, TimeslotType.CLASS));
        }

        timeslotRepository.save(entity);
        context.register(entity);
        return created;
    }

    private boolean upsertBuilding(Map<String, String> row, int rowNumber, ImportContext context) {
        String name = CsvUtils.required(row, "name", rowNumber);

        Building entity = context.buildingByName(name);
        boolean created = entity == null;
        if (created) entity = new Building();

        entity.setName(name);
        String location = CsvUtils.blankToNull(row.get("location"));
        if (location != null || created) entity.setLocation(location);

        buildingRepository.save(entity);
        context.register(entity);
        return created;
    }

    private boolean upsertDepartment(Map<String, String> row, int rowNumber, ImportContext context) {
        String code = CsvUtils.key(CsvUtils.required(row, "code", rowNumber));
        String name = CsvUtils.required(row, "name", rowNumber);

        Department entity = context.departmentByCode(code);
        boolean created = entity == null;
        if (created) entity = new Department();

        entity.setCode(code);
        entity.setName(name);

        Set<String> buildingNames = CsvUtils.splitTokens(row.get("buildingnames"));
        if (!buildingNames.isEmpty()) {
            entity.setBuildingsAllowed(resolveBuildings(buildingNames, context));
        }

        departmentRepository.save(entity);
        context.register(entity);
        return created;
    }

    private boolean upsertRoom(Map<String, String> row, int rowNumber, ImportContext context) {
        String buildingName = CsvUtils.required(row, "buildingname", rowNumber);
        String roomNumber = CsvUtils.required(row, "roomnumber", rowNumber);
        Building building = requireEntity(
            context.buildingByName(buildingName),
            "Unknown building: " + buildingName + " (import buildings first)"
        );

        Room entity = context.room(buildingName, roomNumber);
        boolean created = entity == null;
        if (created) entity = new Room();

        entity.setBuilding(building);
        entity.setRoomNumber(roomNumber);
        String type = CsvUtils.required(row, "type", rowNumber);
        entity.setType(CsvUtils.parseEnum(RoomType.class, type));
        String labSubtype = CsvUtils.blankToNull(row.get("labsubtype"));
        if (labSubtype != null || created) {
            entity.setLabSubtype(CsvUtils.parseEnumOrNull(LabSubtype.class, labSubtype));
        }
        String capacity = CsvUtils.required(row, "capacity", rowNumber);
        entity.setCapacity(Integer.parseInt(capacity));

        Set<String> availableSlots = CsvUtils.splitTokens(row.get("availabletimeslots"));
        if (!availableSlots.isEmpty()) {
            entity.setAvailableTimeslots(resolveTimeslots(availableSlots, context));
        }

        roomRepository.save(entity);
        context.register(entity);
        return created;
    }

    private boolean upsertSubject(Map<String, String> row, int rowNumber, ImportContext context) {
        String departmentCode = CsvUtils.key(CsvUtils.required(row, "departmentcode", rowNumber));
        Department department = requireEntity(
            context.departmentByCode(departmentCode),
            "Unknown departmentCode: " + departmentCode + " (import departments first)"
        );
        String code = CsvUtils.required(row, "code", rowNumber);
        String name = CsvUtils.required(row, "name", rowNumber);

        Subject entity = context.subject(departmentCode, code);
        boolean created = entity == null;
        if (created) entity = new Subject();

        entity.setDepartment(department);
        entity.setCode(code.toUpperCase(java.util.Locale.ROOT));
        entity.setName(name);

        entity.setWeeklyHours(parseRequiredInt(row, "weeklyhours", rowNumber));
        entity.setChunkHours(parseRequiredInt(row, "chunkhours", rowNumber));
        String roomType = CsvUtils.required(row, "roomtyperequired", rowNumber);
        entity.setRoomTypeRequired(CsvUtils.parseEnum(RoomType.class, roomType));
        String labSubtype = CsvUtils.blankToNull(row.get("labsubtyperequired"));
        if (labSubtype != null || created) {
            entity.setLabSubtypeRequired(CsvUtils.parseEnumOrNull(LabSubtype.class, labSubtype));
        }
        String isLab = CsvUtils.blankToNull(row.get("islab"));
        if (isLab != null || created) entity.setLab(CsvUtils.parseBooleanOrDefault(isLab, false));
        String requiresTeacher = CsvUtils.blankToNull(row.get("requiresteacher"));
        if (requiresTeacher != null || created) entity.setRequiresTeacher(CsvUtils.parseBooleanOrDefault(requiresTeacher, true));
        String requiresRoom = CsvUtils.blankToNull(row.get("requiresroom"));
        if (requiresRoom != null || created) entity.setRequiresRoom(CsvUtils.parseBooleanOrDefault(requiresRoom, true));
        String minGap = CsvUtils.blankToNull(row.get("mingapbetweensessions"));
        if (minGap != null || created) entity.setMinGapBetweenSessions(CsvUtils.parseIntOrDefault(minGap, 0));
        String maxPerDay = CsvUtils.blankToNull(row.get("maxsessionsperday"));
        if (maxPerDay != null || created) entity.setMaxSessionsPerDay(CsvUtils.parseIntOrDefault(maxPerDay, 1));

        subjectRepository.save(entity);
        context.register(entity);
        return created;
    }

    private boolean upsertTeacher(Map<String, String> row, int rowNumber, ImportContext context) {
        String employeeId = CsvUtils.required(row, "employeeid", rowNumber);
        String name = CsvUtils.required(row, "name", rowNumber);

        Teacher entity = context.teacherByEmployeeId(employeeId);
        boolean created = entity == null;
        if (created) entity = new Teacher();

        entity.setEmployeeId(employeeId);
        entity.setName(name);
        entity.setMaxDailyHours(CsvUtils.parseIntOrDefault(row.get("maxdailyhours"), created ? 6 : entity.getMaxDailyHours()));
        entity.setMaxWeeklyHours(CsvUtils.parseIntOrDefault(row.get("maxweeklyhours"), created ? 20 : entity.getMaxWeeklyHours()));
        entity.setMaxConsecutiveClasses(CsvUtils.parseIntOrDefault(row.get("maxconsecutiveclasses"), created ? 3 : entity.getMaxConsecutiveClasses()));
        entity.setMovementPenalty(CsvUtils.parseIntOrDefault(row.get("movementpenalty"), created ? 1 : entity.getMovementPenalty()));
        String freeDay = CsvUtils.blankToNull(row.get("preferredfreeday"));
        if (freeDay != null || created) {
            entity.setPreferredFreeDay(CsvUtils.parseEnumOrNull(SchoolDay.class, freeDay));
        }

        Set<String> subjectCodes = CsvUtils.splitTokens(row.get("subjectcodes"));
        if (!subjectCodes.isEmpty()) {
            entity.setSubjects(resolveSubjects(subjectCodes, context));
        }
        Set<String> availableSlots = CsvUtils.splitTokens(row.get("availabletimeslots"));
        if (!availableSlots.isEmpty()) {
            entity.setAvailableTimeslots(resolveTimeslots(availableSlots, context));
        }
        Set<String> preferredBuildings = CsvUtils.splitTokens(row.get("preferredbuildingnames"));
        if (!preferredBuildings.isEmpty()) {
            entity.setPreferredBuildings(resolveBuildings(preferredBuildings, context));
        }

        teacherRepository.save(entity);
        context.register(entity);
        return created;
    }

    private boolean upsertBatch(Map<String, String> row, int rowNumber, ImportContext context) {
        String departmentCode = CsvUtils.key(CsvUtils.required(row, "departmentcode", rowNumber));
        Department department = requireEntity(
            context.departmentByCode(departmentCode),
            "Unknown departmentCode: " + departmentCode + " (import departments first)"
        );
        int year = Integer.parseInt(CsvUtils.required(row, "year", rowNumber));
        String section = CsvUtils.required(row, "section", rowNumber);

        Batch entity = context.batch(departmentCode, year, section);
        boolean created = entity == null;
        if (created) entity = new Batch();

        entity.setDepartment(department);
        entity.setYear(year);
        entity.setSection(section);
        String studentCount = CsvUtils.blankToNull(row.get("studentcount"));
        if (studentCount != null || created) {
            entity.setStudentCount(CsvUtils.parseIntOrDefault(studentCount, 60));
        }
        String preferredFreeDay = CsvUtils.blankToNull(row.get("preferredfreeday"));
        if (preferredFreeDay != null || created) {
            entity.setPreferredFreeDay(CsvUtils.parseEnumOrNull(SchoolDay.class, preferredFreeDay));
        }
        Set<String> workingDaysRaw = CsvUtils.splitTokens(row.get("workingdays"));
        if (!workingDaysRaw.isEmpty()) {
            List<SchoolDay> workingDays = workingDaysRaw.stream()
                .map(token -> CsvUtils.parseEnum(SchoolDay.class, token))
                .toList();
            entity.setWorkingDays(workingDays);
        }

        batchRepository.save(entity);
        context.register(entity);
        return created;
    }

    // =========================================================================
    // Value resolvers (token columns → managed entity references)
    // =========================================================================

    private List<Building> resolveBuildings(Set<String> names, ImportContext context) {
        List<Building> resolved = new ArrayList<>();
        for (String name : names) {
            Building building = requireEntity(context.buildingByName(name), "Unknown building: " + name);
            resolved.add(building);
        }
        return new ArrayList<>(new LinkedHashSet<>(resolved));
    }

    private List<Timeslot> resolveTimeslots(Set<String> tokens, ImportContext context) {
        List<Timeslot> resolved = new ArrayList<>();
        for (String token : tokens) {
            Optional<Long> id = CsvUtils.parseLong(token);
            Timeslot slot;
            if (id.isPresent()) {
                slot = requireEntity(context.timeslotById(id.get()), "Unknown timeslot id: " + token);
            } else {
                String[] parts = token.split("@");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid timeslot token: " + token + " (expected DAY@HH:mm-HH:mm or an id)");
                }
                String[] times = parts[1].split("-");
                if (times.length != 2) {
                    throw new IllegalArgumentException("Invalid timeslot token: " + token + " (expected DAY@HH:mm-HH:mm)");
                }
                slot = requireEntity(
                    context.timeslot(parts[0], times[0], times[1]),
                    "Unknown timeslot: " + token + " (import timeslots first)"
                );
            }
            resolved.add(slot);
        }
        return new ArrayList<>(new LinkedHashSet<>(resolved));
    }

    private List<Subject> resolveSubjects(Set<String> tokens, ImportContext context) {
        List<Subject> resolved = new ArrayList<>();
        for (String token : tokens) {
            Subject subject;
            if (token.contains(":")) {
                String[] parts = token.split(":", 2);
                subject = requireEntity(
                    context.subject(parts[0], parts[1]),
                    "Unknown subject: " + token
                );
            } else {
                subject = context.subjectByCode(token);
                if (subject == null) {
                    throw new IllegalArgumentException(
                        "Unknown or ambiguous subject code: " + token + " — use " + contextNameHint(token) + " format"
                    );
                }
                resolved.add(subject);
                continue;
            }
            resolved.add(subject);
        }
        return new ArrayList<>(new LinkedHashSet<>(resolved));
    }

    private String contextNameHint(String subjectCode) {
        return "DEPT:" + subjectCode;
    }

    // =========================================================================
    // Small helpers
    // =========================================================================

    private static int parseRequiredInt(Map<String, String> row, String column, int rowNumber) {
        return Integer.parseInt(CsvUtils.required(row, column, rowNumber));
    }

    private static <T> T requireEntity(T entity, String message) {
        if (entity == null) throw new IllegalArgumentException(message);
        return entity;
    }
}