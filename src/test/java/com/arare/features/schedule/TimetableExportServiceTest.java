package com.arare.features.schedule;

import com.arare.common.enums.RoomType;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.department.Department;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimetableExportServiceTest {

    @Mock private ScheduleRepository scheduleRepo;
    @Mock private ClassSessionRepository sessionRepo;
    @Mock private BatchRepository batchRepo;
    @Mock private TeacherRepository teacherRepo;
    @Mock private RoomRepository roomRepo;

    @InjectMocks private TimetableExportService service;

    private Schedule schedule;
    private Subject subject;
    private Timeslot slot;
    private ClassSession session;
    private Teacher teacher;
    private Room room;
    private Batch batch;

    @BeforeEach
    void setUp() {
        schedule = Schedule.builder().name("Term 1").build();
        schedule.setId(1L);

        subject = Subject.builder().name("Algorithms").code("CS301").build();
        subject.setId(100L);

        slot = Timeslot.builder()
            .day(SchoolDay.MONDAY).type(TimeslotType.CLASS)
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
            .slotNumber(1).build();
        slot.setId(10L);

        teacher = Teacher.builder().name("Dr. Q").build();
        teacher.setId(7L);

        Department dept = Department.builder().name("CSE").build();
        dept.setId(3L);
        batch = Batch.builder().department(dept).year(2).section("A").build();
        batch.setId(5L);

        room = Room.builder()
            .roomNumber("L-101")
            .type(RoomType.LECTURE)
            .capacity(60)
            .build();
        room.setId(8L);

        session = ClassSession.builder()
            .id(20L).schedule(schedule).subject(subject)
            .batch(batch).teacher(teacher).room(room)
            .timeslot(slot).duration(1).build();
    }

    @Test
    void exportAll_returnsFullFlatCsv() {
        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session));

        byte[] bytes = service.exportCsv(1L, TimetableExportService.View.ALL, null);

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("Algorithms"));
        assertTrue(csv.contains("Dr. Q"));
    }

    @Test
    void exportBatchEntity_filtersToThatBatch() {
        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session));

        byte[] bytes = service.exportCsv(1L, TimetableExportService.View.BATCH, 5L);

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Algorithms"), "matching session should be present");
    }

    @Test
    void exportBatchPerEntity_returnsZipWithOneEntryPerBatch() throws Exception {
        Department dept2 = Department.builder().name("CSE").build();
        dept2.setId(3L);
        Batch otherBatch = Batch.builder().department(dept2).year(1).section("B").build();
        otherBatch.setId(6L);
        ClassSession otherSession = ClassSession.builder()
            .id(21L).schedule(schedule).subject(subject)
            .batch(otherBatch).teacher(teacher).room(room).timeslot(slot).duration(1).build();

        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session, otherSession));

        byte[] bytes = service.exportCsv(1L, TimetableExportService.View.BATCH, null);

        assertEquals('P', bytes[0], "should be a ZIP archive");
        assertEquals('K', bytes[1]);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int entries = 0;
            ByteArrayOutputStream first = new ByteArrayOutputStream();
            while (zis.getNextEntry() != null) {
                entries++;
                byte[] buf = new byte[1024];
                int n;
                while ((n = zis.read(buf)) > 0) {
                    if (entries == 1) first.write(buf, 0, n);
                }
            }
            assertEquals(2, entries, "one CSV file per batch");
            assertTrue(new String(first.toByteArray(), StandardCharsets.UTF_8).contains("Algorithms"));
        }
    }

    @Test
    void exportTeacherPerEntity_separatesSameNameTeachersIntoDistinctFiles() throws Exception {
        Teacher sameName = Teacher.builder().name("Dr. Q").build();
        sameName.setId(8L);
        ClassSession other = ClassSession.builder()
            .id(22L).schedule(schedule).subject(subject)
            .batch(batch).teacher(sameName).room(room).timeslot(slot).duration(1).build();

        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session, other));

        byte[] bytes = service.exportCsv(1L, TimetableExportService.View.TEACHER, null);

        assertEquals('P', bytes[0], "should be a ZIP archive");
        assertEquals('K', bytes[1]);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int entries = 0;
            while (zis.getNextEntry() != null) entries++;
            assertEquals(2, entries, "two distinct teachers with the same name must get separate files");
        }
    }
}
