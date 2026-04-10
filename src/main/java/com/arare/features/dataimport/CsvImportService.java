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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final TimeslotRepository timeslotRepository;
    private final BuildingRepository buildingRepository;
    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;

    @Transactional
    public CsvImportResponse importCsv(String entityTypeRaw, String csvContent) {
        String entityType = normalize(entityTypeRaw);
        List<Map<String, String>> rows = parseRows(csvContent);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Map<String, String> row = rows.get(i);
            try {
                boolean wasCreated = switch (entityType) {
                    case "timeslots" -> upsertTimeslot(row, rowNumber);
                    case "buildings" -> upsertBuilding(row, rowNumber);
                    case "departments" -> upsertDepartment(row, rowNumber);
                    case "rooms" -> upsertRoom(row, rowNumber);
                    case "subjects" -> upsertSubject(row, rowNumber);
                    case "teachers" -> upsertTeacher(row, rowNumber);
                    case "batches" -> upsertBatch(row, rowNumber);
                    default -> throw new IllegalArgumentException("Unsupported entityType: " + entityTypeRaw);
                };
                if (wasCreated) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + rowNumber + ": " + ex.getMessage());
            }
        }

        return new CsvImportResponse(entityType, created, updated, skipped, errors);
    }

    private boolean upsertTimeslot(Map<String, String> row, int rowNumber) {
        SchoolDay day = parseEnum(SchoolDay.class, required(row, "day", rowNumber));
        LocalTime start = LocalTime.parse(required(row, "starttime", rowNumber));
        LocalTime end = LocalTime.parse(required(row, "endtime", rowNumber));
        Integer slotNumber = optionalInt(row.get("slotnumber"));
        TimeslotType type = parseEnumOrDefault(TimeslotType.class, row.get("type"), TimeslotType.CLASS);

        List<Timeslot> matches = timeslotRepository.findByDay(day).stream()
            .filter(t -> t.getStartTime().equals(start) && t.getEndTime().equals(end))
            .toList();

        Timeslot entity;
        boolean created;
        if (matches.isEmpty()) {
            entity = new Timeslot();
            created = true;
        } else {
            entity = matches.get(0);
            created = false;
        }

        entity.setDay(day);
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setSlotNumber(slotNumber);
        entity.setType(type);
        timeslotRepository.save(entity);
        return created;
    }

    private boolean upsertBuilding(Map<String, String> row, int rowNumber) {
        String name = required(row, "name", rowNumber);
        String location = blankToNull(row.get("location"));

        Building entity = buildingRepository.findAll().stream()
            .filter(b -> name.equalsIgnoreCase(b.getName()))
            .findFirst()
            .orElse(null);

        boolean created = entity == null;
        if (entity == null) {
            entity = new Building();
        }

        entity.setName(name);
        entity.setLocation(location);
        buildingRepository.save(entity);
        return created;
    }

    private boolean upsertDepartment(Map<String, String> row, int rowNumber) {
        String name = required(row, "name", rowNumber);
        String code = required(row, "code", rowNumber).toUpperCase(Locale.ROOT);
        Set<String> buildingNames = splitTokens(row.get("buildingnames"));

        Department entity = departmentRepository.findAll().stream()
            .filter(d -> code.equalsIgnoreCase(d.getCode()))
            .findFirst()
            .orElse(null);

        boolean created = entity == null;
        if (entity == null) {
            entity = new Department();
        }

        entity.setName(name);
        entity.setCode(code);

        if (!buildingNames.isEmpty()) {
            Map<String, Building> buildingByName = new HashMap<>();
            for (Building building : buildingRepository.findAll()) {
                buildingByName.put(normalize(building.getName()), building);
            }
            List<Building> allowed = new ArrayList<>();
            for (String token : buildingNames) {
                Building building = buildingByName.get(normalize(token));
                if (building == null) {
                    throw new IllegalArgumentException("Unknown building in buildingNames: " + token);
                }
                allowed.add(building);
            }
            entity.setBuildingsAllowed(allowed);
        }

        departmentRepository.save(entity);
        return created;
    }

    private boolean upsertRoom(Map<String, String> row, int rowNumber) {
        String buildingName = required(row, "buildingname", rowNumber);
        String roomNumber = required(row, "roomnumber", rowNumber);
        RoomType type = parseEnum(RoomType.class, required(row, "type", rowNumber));
        LabSubtype labSubtype = parseEnumOrNull(LabSubtype.class, row.get("labsubtype"));
        int capacity = Integer.parseInt(required(row, "capacity", rowNumber));
        Set<String> availableSlots = splitTokens(row.get("availabletimeslots"));

        Building building = buildingRepository.findAll().stream()
            .filter(b -> buildingName.equalsIgnoreCase(b.getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown building: " + buildingName));

        Room entity = roomRepository.findAll().stream()
            .filter(r -> r.getBuilding() != null
                && Objects.equals(r.getBuilding().getId(), building.getId())
                && roomNumber.equalsIgnoreCase(r.getRoomNumber()))
            .findFirst()
            .orElse(null);

        boolean created = entity == null;
        if (entity == null) {
            entity = new Room();
        }

        entity.setBuilding(building);
        entity.setRoomNumber(roomNumber);
        entity.setType(type);
        entity.setLabSubtype(labSubtype);
        entity.setCapacity(capacity);

        if (!availableSlots.isEmpty()) {
            entity.setAvailableTimeslots(resolveTimeslots(availableSlots));
        }

        roomRepository.save(entity);
        return created;
    }

    private boolean upsertSubject(Map<String, String> row, int rowNumber) {
        String name = required(row, "name", rowNumber);
        String code = blankToNull(row.get("code"));
        String departmentCode = required(row, "departmentcode", rowNumber);
        int weeklyHours = Integer.parseInt(required(row, "weeklyhours", rowNumber));
        int chunkHours = Integer.parseInt(required(row, "chunkhours", rowNumber));
        RoomType roomTypeRequired = parseEnum(RoomType.class, required(row, "roomtyperequired", rowNumber));
        LabSubtype labSubtypeRequired = parseEnumOrNull(LabSubtype.class, row.get("labsubtyperequired"));
        boolean isLab = parseBooleanOrDefault(row.get("islab"), false);
        boolean requiresTeacher = parseBooleanOrDefault(row.get("requiresteacher"), true);
        boolean requiresRoom = parseBooleanOrDefault(row.get("requiresroom"), true);
        int minGapBetweenSessions = parseIntOrDefault(row.get("mingapbetweensessions"), 0);
        int maxSessionsPerDay = parseIntOrDefault(row.get("maxsessionsperday"), 1);

        Department department = departmentRepository.findAll().stream()
            .filter(d -> departmentCode.equalsIgnoreCase(d.getCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown departmentCode: " + departmentCode));

        Subject entity = subjectRepository.findByDepartmentId(department.getId()).stream()
            .filter(s -> subjectMatches(s, name, code))
            .findFirst()
            .orElse(null);

        boolean created = entity == null;
        if (entity == null) {
            entity = new Subject();
        }

        entity.setName(name);
        entity.setCode(code);
        entity.setDepartment(department);
        entity.setWeeklyHours(weeklyHours);
        entity.setChunkHours(chunkHours);
        entity.setRoomTypeRequired(roomTypeRequired);
        entity.setLabSubtypeRequired(labSubtypeRequired);
        entity.setLab(isLab);
        entity.setRequiresTeacher(requiresTeacher);
        entity.setRequiresRoom(requiresRoom);
        entity.setMinGapBetweenSessions(minGapBetweenSessions);
        entity.setMaxSessionsPerDay(maxSessionsPerDay);

        subjectRepository.save(entity);
        return created;
    }

    private boolean upsertTeacher(Map<String, String> row, int rowNumber) {
        String name = required(row, "name", rowNumber);
        Set<String> subjectCodes = splitTokens(row.get("subjectcodes"));
        Set<String> availableSlots = splitTokens(row.get("availabletimeslots"));
        Set<String> preferredBuildings = splitTokens(row.get("preferredbuildingnames"));
        int maxDailyHours = parseIntOrDefault(row.get("maxdailyhours"), 6);
        int maxWeeklyHours = parseIntOrDefault(row.get("maxweeklyhours"), 20);
        int maxConsecutiveClasses = parseIntOrDefault(row.get("maxconsecutiveclasses"), 3);
        int movementPenalty = parseIntOrDefault(row.get("movementpenalty"), 1);
        SchoolDay preferredFreeDay = parseEnumOrNull(SchoolDay.class, row.get("preferredfreeday"));

        Teacher entity = teacherRepository.findAll().stream()
            .filter(t -> name.equalsIgnoreCase(t.getName()))
            .findFirst()
            .orElse(null);

        boolean created = entity == null;
        if (entity == null) {
            entity = new Teacher();
        }

        entity.setName(name);
        entity.setMaxDailyHours(maxDailyHours);
        entity.setMaxWeeklyHours(maxWeeklyHours);
        entity.setMaxConsecutiveClasses(maxConsecutiveClasses);
        entity.setMovementPenalty(movementPenalty);
        entity.setPreferredFreeDay(preferredFreeDay);

        if (!subjectCodes.isEmpty()) {
            entity.setSubjects(resolveSubjects(subjectCodes));
        }
        if (!availableSlots.isEmpty()) {
            entity.setAvailableTimeslots(resolveTimeslots(availableSlots));
        }
        if (!preferredBuildings.isEmpty()) {
            entity.setPreferredBuildings(resolveBuildings(preferredBuildings));
        }

        teacherRepository.save(entity);
        return created;
    }

    private boolean upsertBatch(Map<String, String> row, int rowNumber) {
        String departmentCode = required(row, "departmentcode", rowNumber);
        int year = Integer.parseInt(required(row, "year", rowNumber));
        String section = required(row, "section", rowNumber);
        int studentCount = Integer.parseInt(required(row, "studentcount", rowNumber));
        Set<String> workingDaysRaw = splitTokens(row.get("workingdays"));
        SchoolDay preferredFreeDay = parseEnumOrNull(SchoolDay.class, row.get("preferredfreeday"));

        Department department = departmentRepository.findAll().stream()
            .filter(d -> departmentCode.equalsIgnoreCase(d.getCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown departmentCode: " + departmentCode));

        Batch entity = batchRepository.findByDepartmentIdAndYear(department.getId(), year).stream()
            .filter(b -> section.equalsIgnoreCase(b.getSection()))
            .findFirst()
            .orElse(null);

        boolean created = entity == null;
        if (entity == null) {
            entity = new Batch();
        }

        entity.setDepartment(department);
        entity.setYear(year);
        entity.setSection(section);
        entity.setStudentCount(studentCount);
        entity.setPreferredFreeDay(preferredFreeDay);

        if (!workingDaysRaw.isEmpty()) {
            List<SchoolDay> workingDays = workingDaysRaw.stream()
                .map(token -> parseEnum(SchoolDay.class, token))
                .toList();
            entity.setWorkingDays(workingDays);
        }

        batchRepository.save(entity);
        return created;
    }

    private List<Subject> resolveSubjects(Set<String> subjectCodes) {
        List<Subject> allSubjects = subjectRepository.findAll();
        Map<String, List<Subject>> byCode = new HashMap<>();
        Map<String, Subject> byDeptAndCode = new HashMap<>();

        for (Subject subject : allSubjects) {
            if (subject.getCode() != null && !subject.getCode().isBlank()) {
                String codeKey = normalize(subject.getCode());
                byCode.computeIfAbsent(codeKey, k -> new ArrayList<>()).add(subject);
                if (subject.getDepartment() != null && subject.getDepartment().getCode() != null) {
                    String scopedKey = normalize(subject.getDepartment().getCode()) + ":" + codeKey;
                    byDeptAndCode.put(scopedKey, subject);
                }
            }
        }

        List<Subject> resolved = new ArrayList<>();
        for (String token : subjectCodes) {
            String normalized = normalize(token);
            if (normalized.contains(":")) {
                Subject scoped = byDeptAndCode.get(normalized);
                if (scoped == null) {
                    throw new IllegalArgumentException("Unknown subject token: " + token);
                }
                resolved.add(scoped);
                continue;
            }

            List<Subject> direct = byCode.getOrDefault(normalized, List.of());
            if (direct.isEmpty()) {
                throw new IllegalArgumentException("Unknown subject code: " + token);
            }
            if (direct.size() > 1) {
                throw new IllegalArgumentException("Ambiguous subject code " + token + ", use DEPT:CODE format");
            }
            resolved.add(direct.get(0));
        }
        return dedupe(resolved);
    }

    private List<Timeslot> resolveTimeslots(Set<String> tokens) {
        List<Timeslot> all = timeslotRepository.findAll();
        Map<Long, Timeslot> byId = new HashMap<>();
        Map<String, Timeslot> byDescriptor = new HashMap<>();

        for (Timeslot slot : all) {
            byId.put(slot.getId(), slot);
            String descriptor = normalize(slot.getDay().name()) + "@"
                + slot.getStartTime() + "-" + slot.getEndTime();
            byDescriptor.put(normalize(descriptor), slot);
        }

        List<Timeslot> resolved = new ArrayList<>();
        for (String token : tokens) {
            Optional<Long> id = parseLong(token);
            if (id.isPresent()) {
                Timeslot slot = byId.get(id.get());
                if (slot == null) {
                    throw new IllegalArgumentException("Unknown timeslot id: " + token);
                }
                resolved.add(slot);
                continue;
            }

            Timeslot slot = byDescriptor.get(normalize(token));
            if (slot == null) {
                throw new IllegalArgumentException("Unknown timeslot token: " + token);
            }
            resolved.add(slot);
        }
        return dedupe(resolved);
    }

    private List<Building> resolveBuildings(Set<String> names) {
        Map<String, Building> byName = new HashMap<>();
        for (Building building : buildingRepository.findAll()) {
            byName.put(normalize(building.getName()), building);
        }

        List<Building> resolved = new ArrayList<>();
        for (String name : names) {
            Building building = byName.get(normalize(name));
            if (building == null) {
                throw new IllegalArgumentException("Unknown building: " + name);
            }
            resolved.add(building);
        }
        return dedupe(resolved);
    }

    private static boolean subjectMatches(Subject subject, String name, String code) {
        if (code != null && subject.getCode() != null) {
            return code.equalsIgnoreCase(subject.getCode());
        }
        return name.equalsIgnoreCase(subject.getName());
    }

    private static String required(Map<String, String> row, String key, int rowNumber) {
        String value = blankToNull(row.get(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing required column " + key + " at row " + rowNumber);
        }
        return value;
    }

    private static List<Map<String, String>> parseRows(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new IllegalArgumentException("csvContent cannot be blank");
        }

        String[] lines = csvContent.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException("CSV must include header and at least one data row");
        }

        List<String> headersRaw = parseCsvLine(lines[0]);
        List<String> headers = headersRaw.stream().map(CsvImportService::normalize).toList();

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String value = c < values.size() ? values.get(c).trim() : "";
                row.put(headers.get(c), value);
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String raw) {
        try {
            return Enum.valueOf(enumType, normalize(raw));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid " + enumType.getSimpleName() + " value: " + raw);
        }
    }

    private static <E extends Enum<E>> E parseEnumOrNull(Class<E> enumType, String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        return parseEnum(enumType, value);
    }

    private static <E extends Enum<E>> E parseEnumOrDefault(Class<E> enumType, String raw, E defaultValue) {
        String value = blankToNull(raw);
        if (value == null) {
            return defaultValue;
        }
        return parseEnum(enumType, value);
    }

    private static boolean parseBooleanOrDefault(String raw, boolean defaultValue) {
        String value = blankToNull(raw);
        if (value == null) {
            return defaultValue;
        }
        return switch (normalize(value)) {
            case "TRUE", "YES", "Y", "1" -> true;
            case "FALSE", "NO", "N", "0" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean value: " + raw);
        };
    }

    private static int parseIntOrDefault(String raw, int defaultValue) {
        String value = blankToNull(raw);
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static Integer optionalInt(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private static Optional<Long> parseLong(String raw) {
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Set<String> splitTokens(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return Set.of();
        }
        return Arrays.stream(value.split("[;|]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> List<T> dedupe(List<T> source) {
        return new ArrayList<>(new LinkedHashSet<>(source));
    }
}
