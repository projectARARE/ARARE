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
import com.arare.features.institute.Institute;
import com.arare.features.institute.InstituteRepository;
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
    private final InstituteRepository instituteRepository;
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

    // 
    // Entity upserts
    // 

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

        // Resolve/validate every reference BEFORE touching the (possibly managed)
        // entity so a failure cannot leave a partial dirty-update on commit.
        Institute inst = null;
        Department existing = context.departmentByCode(code);
        if (existing == null || existing.getInstitute() == null) {
            inst = instituteRepository.findAllByOrderByNameAsc().stream().findFirst().orElse(null);
        }
        Set<String> buildingNames = CsvUtils.splitTokens(row.get("buildingnames"));
        List<Building> buildingsAllowed = buildingNames.isEmpty()
            ? null
            : resolveBuildings(buildingNames, context);

        Department entity = existing;
        boolean created = entity == null;
        if (created) entity = new Department();

        entity.setCode(code);
        entity.setName(name);
        // New departments need a home institute. The CSV has no institute
        // column; default to the single/default institute (index 0 by name),
        // which is correct for the common single-institute deployment.
        if (entity.getInstitute() == null && inst != null) {
            entity.setInstitute(inst);
        }
        if (buildingsAllowed != null) {
            entity.setBuildingsAllowed(buildingsAllowed);
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

        // Resolve/validate every reference and scalar BEFORE mutating the
        // (possibly managed) entity so a failure cannot flush a partial update.
        String type = CsvUtils.required(row, "type", rowNumber);
        RoomType roomType = CsvUtils.parseEnum(RoomType.class, type);
        String labSubtype = CsvUtils.blankToNull(row.get("labsubtype"));
        LabSubtype labSubtypeEnum = labSubtype != null ? CsvUtils.parseEnumOrNull(LabSubtype.class, labSubtype) : null;
        String capacity = CsvUtils.required(row, "capacity", rowNumber);
        int capacityInt = Integer.parseInt(capacity);

        Set<String> availableSlots = CsvUtils.splitTokens(row.get("availabletimeslots"));
        List<Timeslot> availableTimeslots = availableSlots.isEmpty()
            ? null
            : resolveTimeslots(availableSlots, context);

        Room entity = context.room(buildingName, roomNumber);
        boolean created = entity == null;
        if (created) entity = new Room();

        entity.setBuilding(building);
        entity.setRoomNumber(roomNumber);
        entity.setType(roomType);
        if (labSubtype != null || created) {
            entity.setLabSubtype(labSubtypeEnum);
        }
        entity.setCapacity(capacityInt);
        if (availableTimeslots != null) {
            entity.setAvailableTimeslots(availableTimeslots);
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

        // Resolve/validate every scalar BEFORE mutating the (possibly managed)
        // entity so a failure cannot flush a partial update.
        int weeklyHours = parseRequiredInt(row, "weeklyhours", rowNumber);
        int chunkHours = parseRequiredInt(row, "chunkhours", rowNumber);
        String roomType = CsvUtils.required(row, "roomtyperequired", rowNumber);
        RoomType roomTypeRequired = CsvUtils.parseEnum(RoomType.class, roomType);
        String labSubtype = CsvUtils.blankToNull(row.get("labsubtyperequired"));
        LabSubtype labSubtypeRequired = labSubtype != null ? CsvUtils.parseEnumOrNull(LabSubtype.class, labSubtype) : null;
        String isLab = CsvUtils.blankToNull(row.get("islab"));
        boolean lab = CsvUtils.parseBooleanOrDefault(isLab, false);
        String requiresTeacher = CsvUtils.blankToNull(row.get("requiresteacher"));
        boolean reqTeacher = CsvUtils.parseBooleanOrDefault(requiresTeacher, true);
        String requiresRoom = CsvUtils.blankToNull(row.get("requiresroom"));
        boolean reqRoom = CsvUtils.parseBooleanOrDefault(requiresRoom, true);
        String minGap = CsvUtils.blankToNull(row.get("mingapbetweensessions"));
        int minGapVal = CsvUtils.parseIntOrDefault(minGap, 0);
        String maxPerDay = CsvUtils.blankToNull(row.get("maxsessionsperday"));
        int maxPerDayVal = CsvUtils.parseIntOrDefault(maxPerDay, 1);

        Subject entity = context.subject(departmentCode, code);
        boolean created = entity == null;
        if (created) entity = new Subject();

        entity.setDepartment(department);
        entity.setCode(code.toUpperCase(java.util.Locale.ROOT));
        entity.setName(name);
        entity.setWeeklyHours(weeklyHours);
        entity.setChunkHours(chunkHours);
        entity.setRoomTypeRequired(roomTypeRequired);
        if (labSubtype != null || created) entity.setLabSubtypeRequired(labSubtypeRequired);
        if (isLab != null || created) entity.setLab(lab);
        if (requiresTeacher != null || created) entity.setRequiresTeacher(reqTeacher);
        if (requiresRoom != null || created) entity.setRequiresRoom(reqRoom);
        if (minGap != null || created) entity.setMinGapBetweenSessions(minGapVal);
        if (maxPerDay != null || created) entity.setMaxSessionsPerDay(maxPerDayVal);

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

        // Resolve/validate every scalar and reference BEFORE mutating the
        // (possibly managed) entity so a failure cannot flush a partial update.
        int maxDailyHours = CsvUtils.parseIntOrDefault(row.get("maxdailyhours"), created ? 6 : entity.getMaxDailyHours());
        int maxWeeklyHours = CsvUtils.parseIntOrDefault(row.get("maxweeklyhours"), created ? 20 : entity.getMaxWeeklyHours());
        int maxConsecutiveClasses = CsvUtils.parseIntOrDefault(row.get("maxconsecutiveclasses"), created ? 3 : entity.getMaxConsecutiveClasses());
        int movementPenalty = CsvUtils.parseIntOrDefault(row.get("movementpenalty"), created ? 1 : entity.getMovementPenalty());
        String freeDay = CsvUtils.blankToNull(row.get("preferredfreeday"));
        SchoolDay preferredFreeDay = CsvUtils.parseEnumOrNull(SchoolDay.class, freeDay);

        Set<String> subjectCodes = CsvUtils.splitTokens(row.get("subjectcodes"));
        List<Subject> subjects = subjectCodes.isEmpty() ? null : resolveSubjects(subjectCodes, context);
        Set<String> availableSlots = CsvUtils.splitTokens(row.get("availabletimeslots"));
        List<Timeslot> availableTimeslots = availableSlots.isEmpty() ? null : resolveTimeslots(availableSlots, context);
        Set<String> preferredBuildings = CsvUtils.splitTokens(row.get("preferredbuildingnames"));
        List<Building> preferredBuildingEntities = preferredBuildings.isEmpty() ? null : resolveBuildings(preferredBuildings, context);

        entity.setEmployeeId(employeeId);
        entity.setName(name);
        entity.setMaxDailyHours(maxDailyHours);
        entity.setMaxWeeklyHours(maxWeeklyHours);
        entity.setMaxConsecutiveClasses(maxConsecutiveClasses);
        entity.setMovementPenalty(movementPenalty);
        if (freeDay != null || created) entity.setPreferredFreeDay(preferredFreeDay);
        if (subjects != null) entity.setSubjects(subjects);
        if (availableTimeslots != null) entity.setAvailableTimeslots(availableTimeslots);
        if (preferredBuildingEntities != null) entity.setPreferredBuildings(preferredBuildingEntities);

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

        // Resolve/validate every scalar BEFORE mutating the (possibly managed)
        // entity so a failure cannot flush a partial update.
        String studentCount = CsvUtils.blankToNull(row.get("studentcount"));
        int studentCountVal = CsvUtils.parseIntOrDefault(studentCount, 60);
        String preferredFreeDay = CsvUtils.blankToNull(row.get("preferredfreeday"));
        SchoolDay preferredFreeDayVal = CsvUtils.parseEnumOrNull(SchoolDay.class, preferredFreeDay);
        Set<String> workingDaysRaw = CsvUtils.splitTokens(row.get("workingdays"));
        List<SchoolDay> workingDays = workingDaysRaw.isEmpty() ? null
            : workingDaysRaw.stream().map(token -> CsvUtils.parseEnum(SchoolDay.class, token)).toList();

        Batch entity = context.batch(departmentCode, year, section);
        boolean created = entity == null;
        if (created) entity = new Batch();

        entity.setDepartment(department);
        entity.setYear(year);
        entity.setSection(section);
        if (studentCount != null || created) entity.setStudentCount(studentCountVal);
        if (preferredFreeDay != null || created) entity.setPreferredFreeDay(preferredFreeDayVal);
        if (workingDays != null) {
            entity.getWorkingDays().clear();
            entity.getWorkingDays().addAll(workingDays);
        }

        batchRepository.save(entity);
        context.register(entity);
        return created;
    }

    // 
    // Value resolvers (token columns → managed entity references)
    // 

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

    // 
    // Small helpers
    // 

    private static int parseRequiredInt(Map<String, String> row, String column, int rowNumber) {
        return Integer.parseInt(CsvUtils.required(row, column, rowNumber));
    }

    private static <T> T requireEntity(T entity, String message) {
        if (entity == null) throw new IllegalArgumentException(message);
        return entity;
    }
}