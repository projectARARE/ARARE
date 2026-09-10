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
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Preloaded natural-key indexes used by all CSV import paths.
 *
 * <p>Indexes are populated once per import from the database, then updated
 * in-memory as files are processed. All lookups are O(1) — no per-row
 * database scans — so imports of any size stay linear.
 */
@RequiredArgsConstructor
public class ImportContext {

    private final TimeslotRepository timeslotRepository;
    private final BuildingRepository buildingRepository;
    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;

    private final Map<String, List<Department>> deptByCode = new HashMap<>();
    private final Set<String> ambiguousDeptCodes = new HashSet<>();
    private final Map<String, Building> buildingByKey = new HashMap<>();
    private final Set<String> ambiguousBuildingNames = new HashSet<>();
    private final Map<String, Timeslot> timeslotByKey = new HashMap<>();
    private final Map<Long, Timeslot> timeslotById = new HashMap<>();
    private final Map<String, Room> roomByKey = new HashMap<>();
    private final Map<String, Subject> subjectByKey = new HashMap<>();
    private final Map<String, Subject> subjectByCode = new HashMap<>();
    private final Set<String> ambiguousSubjectCodes = new HashSet<>();
    private final Map<String, Teacher> teacherByEmployeeId = new HashMap<>();
    private final Map<String, Batch> batchByKey = new HashMap<>();

    /** Prepares the indexes by loading all current entities from the database. */
    public void loadFromDatabase() {
        departmentRepository.findAll().forEach(this::indexDepartment);
        buildingRepository.findAll().forEach(this::indexBuilding);
        timeslotRepository.findAll().forEach(t -> {
            timeslotById.put(t.getId(), t);
            timeslotByKey.putIfAbsent(
                CsvUtils.timeslotKey(t.getDay().name(), t.getStartTime().toString(), t.getEndTime().toString()), t);
        });
        roomRepository.findAll().forEach(r -> {
            if (r.getBuilding() != null) {
                roomByKey.putIfAbsent(CsvUtils.roomKey(r.getBuilding().getName(), r.getRoomNumber()), r);
            }
        });
        subjectRepository.findAll().forEach(s -> {
            if (s.getDepartment() != null && s.getCode() != null) {
                subjectByKey.putIfAbsent(CsvUtils.subjectKey(s.getDepartment().getCode(), s.getCode()), s);
                indexSubjectByCode(s);
            }
        });
        teacherRepository.findAll().stream()
            .filter(t -> t.getEmployeeId() != null && !t.getEmployeeId().isBlank())
            .forEach(t -> teacherByEmployeeId.putIfAbsent(CsvUtils.key(t.getEmployeeId()), t));
        batchRepository.findAll().forEach(b -> {
            if (b.getDepartment() != null) {
                batchByKey.putIfAbsent(CsvUtils.batchKey(b.getDepartment().getCode(), b.getYear(), b.getSection()), b);
            }
        });
    }

    /**
     * Lookups
     */

    public Department departmentByCode(String code) {
        String key = CsvUtils.key(code);
        if (ambiguousDeptCodes.contains(key)) return null;
        List<Department> list = deptByCode.get(key);
        if (list == null || list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);
        ambiguousDeptCodes.add(key);
        return null;
    }

    public Building buildingByName(String name) {
        String key = CsvUtils.key(name);
        return ambiguousBuildingNames.contains(key) ? null : buildingByKey.get(key);
    }

    public boolean isAmbiguousBuildingName(String name) {
        return ambiguousBuildingNames.contains(CsvUtils.key(name));
    }

    public Timeslot timeslot(String day, String start, String end) {
        return timeslotByKey.get(CsvUtils.timeslotKey(day, start, end));
    }

    public Timeslot timeslotById(Long id) {
        return timeslotById.get(id);
    }

    public Room room(String buildingName, String roomNumber) {
        return roomByKey.get(CsvUtils.roomKey(buildingName, roomNumber));
    }

    public Subject subject(String deptCode, String subjectCode) {
        return subjectByKey.get(CsvUtils.subjectKey(deptCode, subjectCode));
    }

    /**
     * Looks up a subject by its bare code; returns {@code null} when the code
     * belongs to multiple departments (ambiguous) or does not exist.
     */
    public Subject subjectByCode(String code) {
        String key = CsvUtils.key(code);
        return ambiguousSubjectCodes.contains(key) ? null : subjectByCode.get(key);
    }

    /** Returns true when at least one entity of the given type is known. */
    public boolean containsAny(CsvEntityType type) {
        return switch (type) {
            case TIMESLOTS -> !timeslotByKey.isEmpty();
            case BUILDINGS -> !buildingByKey.isEmpty();
            case DEPARTMENTS -> !deptByCode.isEmpty();
            case ROOMS -> !roomByKey.isEmpty();
            case SUBJECTS -> !subjectByKey.isEmpty();
            case TEACHERS -> !teacherByEmployeeId.isEmpty();
            case BATCHES -> !batchByKey.isEmpty();
        };
    }

    private void indexSubjectByCode(Subject subject) {
        String codeKey = CsvUtils.key(subject.getCode());
        if (ambiguousSubjectCodes.contains(codeKey)) return;
        if (subjectByCode.containsKey(codeKey) && subjectByCode.get(codeKey) != subject) {
            subjectByCode.remove(codeKey);
            ambiguousSubjectCodes.add(codeKey);
        } else if (!subjectByCode.containsKey(codeKey)) {
            subjectByCode.put(codeKey, subject);
        }
    }

    private void indexBuilding(Building building) {
        String key = CsvUtils.key(building.getName());
        if (ambiguousBuildingNames.contains(key)) return;
        Building existing = buildingByKey.get(key);
        if (existing != null && existing != building) {
            buildingByKey.remove(key);
            ambiguousBuildingNames.add(key);
        } else if (existing == null) {
            buildingByKey.put(key, building);
        }
    }

    private void indexDepartment(Department dept) {
        String key = CsvUtils.key(dept.getCode());
        if (ambiguousDeptCodes.contains(key)) return;
        List<Department> existing = deptByCode.computeIfAbsent(key, k -> new ArrayList<>());
        if (existing.isEmpty()) {
            existing.add(dept);
        } else if (!existing.contains(dept)) {
            existing.add(dept);
            if (existing.size() > 1) {
                ambiguousDeptCodes.add(key);
            }
        }
    }

    public Teacher teacherByEmployeeId(String employeeId) {
        return teacherByEmployeeId.get(CsvUtils.key(employeeId));
    }

    public Batch batch(String deptCode, int year, String section) {
        return batchByKey.get(CsvUtils.batchKey(deptCode, year, section));
    }

    /**
     * Registrations (called after an upsert so later files can reference it)
     */

    public void register(Department department) {
        indexDepartment(department);
    }

    public void register(Building building) {
        indexBuilding(building);
    }

    public void register(Timeslot timeslot) {
        timeslotById.put(timeslot.getId(), timeslot);
        timeslotByKey.put(
            CsvUtils.timeslotKey(timeslot.getDay().name(), timeslot.getStartTime().toString(), timeslot.getEndTime().toString()),
            timeslot);
    }

    public void register(Room room) {
        if (room.getBuilding() != null) {
            roomByKey.put(CsvUtils.roomKey(room.getBuilding().getName(), room.getRoomNumber()), room);
        }
    }

    public void register(Subject subject) {
        if (subject.getDepartment() != null && subject.getCode() != null) {
            subjectByKey.put(CsvUtils.subjectKey(subject.getDepartment().getCode(), subject.getCode()), subject);
            indexSubjectByCode(subject);
        }
    }

    public void register(Teacher teacher) {
        if (teacher.getEmployeeId() != null && !teacher.getEmployeeId().isBlank()) {
            teacherByEmployeeId.put(CsvUtils.key(teacher.getEmployeeId()), teacher);
        }
    }

    public void register(Batch batch) {
        if (batch.getDepartment() != null) {
            batchByKey.put(CsvUtils.batchKey(batch.getDepartment().getCode(), batch.getYear(), batch.getSection()), batch);
        }
    }
}