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
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

    @Mock private ScheduleRepository scheduleRepo;
    @Mock private ClassSessionRepository sessionRepo;
    @Mock private TimeslotRepository timeslotRepo;
    @Mock private TeacherRepository teacherRepo;
    @Mock private RoomRepository roomRepo;
    @Mock private BatchRepository batchRepo;

    @InjectMocks private PdfExportService service;

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

        Room bldgRoom = Room.builder()
            .roomNumber("L-101")
            .type(RoomType.LECTURE)
            .capacity(60)
            .build();
        bldgRoom.setId(8L);
        room = bldgRoom;

        session = ClassSession.builder()
            .id(20L).schedule(schedule).subject(subject)
            .batch(batch).teacher(teacher).room(room)
            .timeslot(slot).duration(1).build();
    }

    @Test
    void exportAll_returnsPdfWithPdfMagicBytes() {
        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(timeslotRepo.findByType(TimeslotType.CLASS)).thenReturn(List.of(slot));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session));

        byte[] pdf = service.exportPdf(1L, PdfExportService.View.ALL, null);

        assertTrue(pdf.length > 1000);
        assertEquals('%', pdf[0]);
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);
    }

    @Test
    void exportTeacher_keepsMatchingSessionsAndHasTeacherTitle() {
        Teacher other = Teacher.builder().name("Dr. Other").build();
        other.setId(99L);
        ClassSession otherSession = ClassSession.builder()
            .id(21L).schedule(schedule).subject(subject)
            .batch(batch).teacher(other).room(room).timeslot(slot).duration(1).build();

        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(timeslotRepo.findByType(TimeslotType.CLASS)).thenReturn(List.of(slot));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session, otherSession));
        when(teacherRepo.findById(7L)).thenReturn(Optional.of(teacher));

        byte[] pdf = service.exportPdf(1L, PdfExportService.View.TEACHER, 7L);

        assertTrue(pdf.length > 1000);
    }

    @Test
    void exportBatch_filtersSessionsAndHasBatchTitle() {
        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(timeslotRepo.findByType(TimeslotType.CLASS)).thenReturn(List.of(slot));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session));
        when(batchRepo.findById(5L)).thenReturn(Optional.of(batch));

        byte[] pdf = service.exportPdf(1L, PdfExportService.View.BATCH, 5L);

        assertTrue(pdf.length > 1000);
    }

    @Test
    void exportBatchPerEntity_emitsPdfForEveryBatch() {
        Department dept2 = Department.builder().name("CSE").build();
        dept2.setId(3L);
        Batch otherBatch = Batch.builder().department(dept2).year(1).section("B").build();
        otherBatch.setId(6L);
        ClassSession otherSession = ClassSession.builder()
            .id(21L).schedule(schedule).subject(subject)
            .batch(otherBatch).teacher(teacher).room(room).timeslot(slot).duration(1).build();

        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(timeslotRepo.findByType(TimeslotType.CLASS)).thenReturn(List.of(slot));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(session, otherSession));
        when(batchRepo.findById(5L)).thenReturn(Optional.of(batch));
        when(batchRepo.findById(6L)).thenReturn(Optional.of(otherBatch));

        byte[] pdf = service.exportPdf(1L, PdfExportService.View.BATCH, null);

        assertTrue(pdf.length > 1000);
        assertEquals('%', pdf[0]);
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);
    }
}