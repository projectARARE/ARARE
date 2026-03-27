package com.arare.features.classsession;

import com.arare.features.subject.Subject;
import com.arare.features.room.RoomRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @InjectMocks
    private ClassSessionServiceImpl service;

    private ClassSession session;

    @BeforeEach
    void setUp() {
        Subject subject = Subject.builder()
            .name("Algorithms")
            .weeklyHours(4)
            .chunkHours(1)
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
}
