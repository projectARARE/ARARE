package com.arare.features.classsession;

import com.arare.common.enums.RoomType;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.department.Department;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassSessionServiceImplTest {

    @Mock
    private ClassSessionRepository repo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private TimeslotRepository timeslotRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private SubjectRepository subjectRepo;

    @Mock
    private BatchRepository batchRepo;

    @Mock
    private ClassSectionRepository sectionRepo;

    @InjectMocks
    private ClassSessionServiceImpl service;

    private ClassSession session;

    @BeforeEach
    void setUp() {
        Subject subject = Subject.builder()
            .name("Algorithms")
            .weeklyHours(4)
            .chunkHours(1)
            .requiresTeacher(false)
            .build();
        subject.setId(100L);

        Teacher teacher = Teacher.builder().name("Dr. T").build();
        teacher.setId(200L);

        session = ClassSession.builder()
            .id(1L)
            .subject(subject)
            .teacher(teacher)
            .room(null)
            .timeslot(null)
            .duration(1)
            .isLocked(false)
            .build();
    }

    @Test
    void clearsTeacherWhenClearFlagTrue() {
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(repo.save(session)).thenReturn(session);

        service.updateAssignment(1L, new SessionAssignmentRequest(
            null, null, null, false,
            true, false, false
        ));

        assertNull(session.getTeacher());
    }

    @Test
    void keepsTeacherWhenClearFlagFalse() {
        Teacher before = session.getTeacher();
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(repo.save(session)).thenReturn(session);

        service.updateAssignment(1L, new SessionAssignmentRequest(
            null, null, null, false,
            false, false, false
        ));

        assertSame(before, session.getTeacher());
    }

    // Server-side enforcement (L11): mirror the solver HARD constraints

    @Test
    void rejectsLockedSessionAssignmentEdit() {
        session.setLocked(true);
        when(repo.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(null, null, null, null, true, false, false)));
    }

    @Test
    void rejectsClearingTeacherWhenSubjectRequiresTeacher() {
        session.getSubject().setRequiresTeacher(true);
        session.setTeacher(null);
        when(repo.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(null, null, null, null, true, false, false)));
    }

    @Test
    void rejectsTeacherAssignmentWhenSubjectDoesNotRequireTeacher() {
        Teacher t = Teacher.builder().name("Dr. U").subjects(List.of(session.getSubject())).build();
        t.setId(210L);
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(teacherRepo.findById(210L)).thenReturn(Optional.of(t));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(210L, null, null, null, false, false, false)));
    }

    @Test
    void rejectsTeacherNotQualifiedForSubject() {
        session.getSubject().setRequiresTeacher(true);
        session.setTeacher(null);
        Teacher t = Teacher.builder().name("Dr. Q").build(); // no subjects
        t.setId(220L);
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(teacherRepo.findById(220L)).thenReturn(Optional.of(t));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(220L, null, null, null, false, false, false)));
    }

    @Test
    void rejectsRoomCapacityTooSmall() {
        session.getSubject().setRequiresRoom(true);
        session.getSubject().setRequiresTeacher(true);
        session.getSubject().setRoomTypeRequired(RoomType.LECTURE);
        session.setTeacher(null);

        com.arare.features.batch.Batch batch = com.arare.features.batch.Batch.builder()
            .studentCount(60).year(2).section("A").build();
        batch.setId(5L);
        session.setBatch(batch);

        Room room = Room.builder().roomNumber("B101").type(RoomType.LECTURE).capacity(30).build();
        room.setId(310L);
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(roomRepo.findById(310L)).thenReturn(Optional.of(room));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(null, 310L, null, null, false, false, false)));
    }

    @Test
    void rejectsRoomTypeMismatch() {
        session.getSubject().setRequiresRoom(true);
        session.getSubject().setRequiresTeacher(true);
        session.getSubject().setRoomTypeRequired(RoomType.LAB);
        session.setTeacher(null);

        Room room = Room.builder().roomNumber("B102").type(RoomType.LECTURE).capacity(80).build();
        room.setId(320L);
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(roomRepo.findById(320L)).thenReturn(Optional.of(room));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(null, 320L, null, null, false, false, false)));
    }

    @Test
    void rejectsBreakOrBlockedTimeslot() {
        Timeslot ts = Timeslot.builder()
            .day(SchoolDay.MONDAY).type(TimeslotType.BREAK)
            .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0))
            .slotNumber(5).build();
        ts.setId(410L);
        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(timeslotRepo.findById(410L)).thenReturn(Optional.of(ts));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(null, null, 410L, null, false, false, false)));
    }

    @Test
    void rejectsTeacherDoubleBookedAtSameTime() {
        Schedule schedule = new Schedule();
        schedule.setId(300L);
        session.setSchedule(schedule);
        session.setTeacher(null);
        session.getSubject().setRequiresTeacher(true);

        Teacher t = Teacher.builder().name("Dr. Q").subjects(List.of(session.getSubject())).build();
        t.setId(220L);

        Timeslot ts = Timeslot.builder()
            .day(SchoolDay.MONDAY).type(TimeslotType.CLASS)
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
            .slotNumber(1).build();
        ts.setId(410L);

        ClassSession other = ClassSession.builder()
            .id(2L).schedule(schedule).subject(session.getSubject())
            .teacher(t).timeslot(ts).duration(1).build();

        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(teacherRepo.findById(220L)).thenReturn(Optional.of(t));
        when(timeslotRepo.findById(410L)).thenReturn(Optional.of(ts));
        when(repo.findByScheduleId(300L)).thenReturn(List.of(other));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(220L, null, 410L, null, false, false, false)));
    }

    @Test
    void acceptsValidReassignmentWhenNoHardViolation() {
        Schedule schedule = new Schedule();
        schedule.setId(300L);
        session.setSchedule(schedule);
        session.setTeacher(null);
        session.getSubject().setRequiresTeacher(true);
        session.getSubject().setRoomTypeRequired(RoomType.LECTURE);

        Teacher t = Teacher.builder().name("Dr. Q").subjects(List.of(session.getSubject())).build();
        t.setId(220L);

        Timeslot ts = Timeslot.builder()
            .day(SchoolDay.MONDAY).type(TimeslotType.CLASS)
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
            .slotNumber(1).build();
        ts.setId(410L);

        Room room = Room.builder().roomNumber("B101").type(RoomType.LECTURE).capacity(80).build();
        room.setId(310L);
        com.arare.features.building.Building building = com.arare.features.building.Building.builder()
            .name("Main Block").build();
        building.setId(7L);
        room.setBuilding(building);

        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(teacherRepo.findById(220L)).thenReturn(Optional.of(t));
        when(timeslotRepo.findById(410L)).thenReturn(Optional.of(ts));
        when(roomRepo.findById(310L)).thenReturn(Optional.of(room));
        when(repo.findByScheduleId(300L)).thenReturn(List.of());
        when(repo.save(session)).thenReturn(session);

        service.updateAssignment(1L, new SessionAssignmentRequest(
            220L, 310L, 410L, null, false, false, false));

        assertSame(t, session.getTeacher());
        assertSame(room, session.getRoom());
        assertSame(ts, session.getTimeslot());
    }

    @Test
    void rejectsUnavailableTeacherAtTimeslot() {
        Schedule schedule = new Schedule();
        schedule.setId(300L);
        session.setSchedule(schedule);
        session.setTeacher(null);
        session.getSubject().setRequiresTeacher(true);

        Timeslot ts = Timeslot.builder()
            .day(SchoolDay.MONDAY).type(TimeslotType.CLASS)
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
            .slotNumber(1).build();
        ts.setId(410L);

        Timeslot otherSlot = Timeslot.builder()
            .day(SchoolDay.TUESDAY).type(TimeslotType.CLASS)
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
            .slotNumber(1).build();
        otherSlot.setId(411L);

        Teacher t = Teacher.builder()
            .name("Dr. Q")
            .subjects(List.of(session.getSubject()))
            .availableTimeslots(List.of(otherSlot))
            .build();
        t.setId(220L);

        when(repo.findById(1L)).thenReturn(Optional.of(session));
        when(teacherRepo.findById(220L)).thenReturn(Optional.of(t));
        when(timeslotRepo.findById(410L)).thenReturn(Optional.of(ts));

        assertThrows(IllegalArgumentException.class, () -> service.updateAssignment(1L,
            new SessionAssignmentRequest(220L, null, 410L, null, false, false, false)));
        verify(repo, never()).save(any());
    }

    // Manual session creation (right-click "add session")

    private Department dept;
    private Schedule schedule;
    private Batch batch;

    @BeforeEach
    void setUpCreate() {
        dept = Department.builder().name("CS").code("CS").build();
        dept.setId(9L);
        schedule = new Schedule();
        schedule.setId(300L);
        batch = Batch.builder().studentCount(60).year(2).section("A").department(dept).build();
        batch.setId(5L);
    }

    @Test
    void createPersistsSessionWithBatchAndSubjectChunkSize() {
        Subject subject = Subject.builder()
            .name("Algorithms").weeklyHours(4).chunkHours(2)
            .requiresTeacher(false).requiresRoom(true).build();
        subject.setId(100L);

        when(scheduleRepo.findById(300L)).thenReturn(Optional.of(schedule));
        when(subjectRepo.findById(100L)).thenReturn(Optional.of(subject));
        when(batchRepo.findById(5L)).thenReturn(Optional.of(batch));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClassSessionResponse resp = service.create(new SessionCreateRequest(
            300L, 100L, 5L, null, null, null, null, null, null));

        assertEquals("Algorithms", resp.subjectName());
        assertEquals("CS-2A", resp.batchLabel());
        assertEquals(2, resp.duration());
        assertFalse(resp.isLocked());

        ArgumentCaptor<ClassSession> captor = ArgumentCaptor.forClass(ClassSession.class);
        verify(repo).save(captor.capture());
        assertSame(batch, captor.getValue().getBatch());
        assertNull(captor.getValue().getSection());
    }

    @Test
    void createUsesProvidedDurationAndLock() {
        Subject subject = Subject.builder()
            .name("Algorithms").weeklyHours(4).chunkHours(2)
            .requiresTeacher(false).build();
        subject.setId(100L);

        when(scheduleRepo.findById(300L)).thenReturn(Optional.of(schedule));
        when(subjectRepo.findById(100L)).thenReturn(Optional.of(subject));
        when(batchRepo.findById(5L)).thenReturn(Optional.of(batch));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new SessionCreateRequest(
            300L, 100L, 5L, null, null, null, null, 3, true));

        ArgumentCaptor<ClassSession> captor = ArgumentCaptor.forClass(ClassSession.class);
        verify(repo).save(captor.capture());
        assertEquals(3, captor.getValue().getDuration());
        assertEquals(true, captor.getValue().isLocked());
    }

    @Test
    void createRejectsMissingBatchAndSection() {
        when(scheduleRepo.findById(300L)).thenReturn(Optional.of(schedule));
        when(subjectRepo.findById(100L)).thenReturn(Optional.of(Subject.builder().name("S").build()));

        assertThrows(IllegalArgumentException.class, () -> service.create(
            new SessionCreateRequest(300L, 100L, null, null, null, null, null, null, null)));
        verify(repo, never()).save(any());
    }

    @Test
    void createRejectsBothBatchAndSection() {
        ClassSection section = ClassSection.builder().build();
        section.setId(6L);
        when(scheduleRepo.findById(300L)).thenReturn(Optional.of(schedule));
        when(subjectRepo.findById(100L)).thenReturn(Optional.of(Subject.builder().name("S").build()));

        assertThrows(IllegalArgumentException.class, () -> service.create(
            new SessionCreateRequest(300L, 100L, 5L, 6L, null, null, null, null, null)));
        verify(repo, never()).save(any());
    }

    @Test
    void createRejectsTeacherDoubleBookedAtSameTime() {
        Subject subject = Subject.builder()
            .name("Algorithms").weeklyHours(4).chunkHours(1)
            .requiresTeacher(true).build();
        subject.setId(100L);

        Teacher t = Teacher.builder().name("Dr. Q").subjects(List.of(subject)).build();
        t.setId(220L);

        Timeslot ts = Timeslot.builder()
            .day(SchoolDay.MONDAY).type(TimeslotType.CLASS)
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
            .slotNumber(1).build();
        ts.setId(410L);

        ClassSession other = ClassSession.builder()
            .id(2L).schedule(schedule).subject(subject)
            .teacher(t).timeslot(ts).duration(1).build();

        when(scheduleRepo.findById(300L)).thenReturn(Optional.of(schedule));
        when(subjectRepo.findById(100L)).thenReturn(Optional.of(subject));
        when(batchRepo.findById(5L)).thenReturn(Optional.of(batch));
        when(teacherRepo.findById(220L)).thenReturn(Optional.of(t));
        when(timeslotRepo.findById(410L)).thenReturn(Optional.of(ts));
        when(repo.findByScheduleId(300L)).thenReturn(List.of(other));

        assertThrows(IllegalArgumentException.class, () -> service.create(
            new SessionCreateRequest(300L, 100L, 5L, null, 220L, null, 410L, null, null)));
        verify(repo, never()).save(any());
    }

    @Test
    void delete_removesExistingSession() {
        when(repo.existsById(55L)).thenReturn(true);

        service.delete(55L);

        verify(repo).deleteById(55L);
    }

    @Test
    void delete_throwsWhenSessionMissing() {
        when(repo.existsById(55L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.delete(55L));
        verify(repo, never()).deleteById(any());
    }
}