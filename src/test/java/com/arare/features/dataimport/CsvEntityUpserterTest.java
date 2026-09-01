package com.arare.features.dataimport;

import com.arare.features.batch.BatchRepository;
import com.arare.features.building.BuildingRepository;
import com.arare.features.department.Department;
import com.arare.features.department.DepartmentRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.InjectMocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvEntityUpserterTest {

    @Mock private TimeslotRepository timeslotRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private BatchRepository batchRepository;

    @InjectMocks
    private CsvEntityUpserter upserter;

    @Test
    void teacherWithSameNameButNewEmployeeIdCreatesNewTeacher() {
        // A teacher with the same NAME exists but a different employeeId
        // the upserter must create a NEW entity, never match on name.
        Teacher existing = new Teacher();
        existing.setId(1L);
        existing.setEmployeeId("EMP-OLD");
        existing.setName("Dr. Same");
        existing.setMaxDailyHours(6);
        existing.setMaxWeeklyHours(20);
        existing.setMaxConsecutiveClasses(3);
        existing.setMovementPenalty(1);
        when(teacherRepository.findAll()).thenReturn(List.of(existing));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        boolean created = upserter.upsert(CsvEntityType.TEACHERS,
            row(Map.of(
                "employeeid", "EMP-NEW",
                "name", "Dr. Same",
                "maxdailyhours", "5")),
            2, context);

        assertTrue(created, "same name + different employeeId must create");
    }

    @Test
    void teacherUpdateKeepsExistingValuesForBlankColumns() {
        Teacher existing = new Teacher();
        existing.setId(1L);
        existing.setEmployeeId("EMP1");
        existing.setName("Dr. A");
        existing.setMaxDailyHours(4);
        existing.setMaxWeeklyHours(15);
        existing.setMaxConsecutiveClasses(2);
        existing.setMovementPenalty(2);
        when(teacherRepository.findAll()).thenReturn(List.of(existing));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        // Partial update: only maxDailyHours + name provided.
        boolean created = upserter.upsert(CsvEntityType.TEACHERS,
            row(Map.of("employeeid", "EMP1", "name", "Dr. A Renamed", "maxdailyhours", "7")),
            2, context);

        assertFalse(created, "existing employeeId must update");
        assertEquals("Dr. A Renamed", existing.getName());
        assertEquals(7, existing.getMaxDailyHours());
        assertEquals(15, existing.getMaxWeeklyHours(), "blank column keeps existing value");
        assertEquals(2, existing.getMaxConsecutiveClasses());
    }

    @Test
    void subjectMissingDepartmentFailsWithContext() {
        when(departmentRepository.findAll()).thenReturn(List.of());

        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> upserter.upsert(CsvEntityType.SUBJECTS,
                row(Map.of("departmentcode", "CSE", "code", "CSE201", "name", "DS")),
                2, context));

        assertTrue(ex.getMessage().contains("CSE"));
    }

    @Test
    void subjectLookupByDepartmentAndCode() {
        Department dept = Department.builder().code("CSE").name("CS").build();
        dept.setId(1L);
        Subject subject = new Subject();
        subject.setId(10L);
        subject.setDepartment(dept);
        subject.setCode("CSE201");
        when(departmentRepository.findAll()).thenReturn(List.of(dept));
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        boolean created = upserter.upsert(CsvEntityType.SUBJECTS,
            row(Map.of("departmentcode", "CSE", "code", "CSE202", "name", "DBMS",
                "weeklyhours", "3", "chunkhours", "1", "roomtyperequired", "LECTURE")),
            2, context);

        assertTrue(created);

        // Same department+code again → update.
        boolean again = upserter.upsert(CsvEntityType.SUBJECTS,
            row(Map.of("departmentcode", "CSE", "code", "CSE202", "name", "DBMS II",
                "weeklyhours", "4", "chunkhours", "1", "roomtyperequired", "LECTURE")),
            3, context);
        assertFalse(again);
    }

    @Test
    void timeslotUpsertByDayStartEnd() {
        when(timeslotRepository.findAll()).thenReturn(List.of());
        when(timeslotRepository.save(any(Timeslot.class))).thenAnswer(inv -> {
            Timeslot t = inv.getArgument(0);
            t.setId(5L);
            return t;
        });

        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        boolean created = upserter.upsert(CsvEntityType.TIMESLOTS,
            row(Map.of("day", "MONDAY", "startTime", "09:00", "endTime", "10:00", "type", "CLASS")),
            2, context);

        assertTrue(created);

        // Same natural key again → update, not create.
        boolean again = upserter.upsert(CsvEntityType.TIMESLOTS,
            row(Map.of("day", "MONDAY", "startTime", "09:00", "endTime", "10:00", "type", "BREAK")),
            3, context);
        assertFalse(again);
    }

    private static Map<String, String> row(Map<String, String> values) {
        Map<String, String> normalized = new HashMap<>();
        values.forEach((k, v) -> normalized.put(CsvUtils.normalizeHeader(k), v));
        return normalized;
    }
}