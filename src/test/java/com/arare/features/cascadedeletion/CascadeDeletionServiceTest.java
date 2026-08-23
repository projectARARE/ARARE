package com.arare.features.cascadedeletion;

import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.event.EventRepository;
import com.arare.features.preallocation.PreAllocationRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CascadeDeletionServiceTest {

    private ClassSessionRepository sessionRepo;
    private ScheduleRepository scheduleRepo;
    private PreAllocationRepository preAllocationRepo;
    private TeacherRepository teacherRepo;
    private RoomRepository roomRepo;
    private TimeslotRepository timeslotRepo;
    private EventRepository eventRepo;
    private CascadeDeletionService service;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(ClassSessionRepository.class);
        scheduleRepo = mock(ScheduleRepository.class);
        preAllocationRepo = mock(PreAllocationRepository.class);
        teacherRepo = mock(TeacherRepository.class);
        roomRepo = mock(RoomRepository.class);
        timeslotRepo = mock(TimeslotRepository.class);
        eventRepo = mock(EventRepository.class);
        service = new CascadeDeletionService(preAllocationRepo, sessionRepo, scheduleRepo, teacherRepo, roomRepo, timeslotRepo, eventRepo);
    }

    @Test
    void purgeScheduleTreePurgesDescendantsAndSelf() {
        Schedule child = Schedule.builder().build();
        child.setId(2L);
        Schedule grandchild = Schedule.builder().build();
        grandchild.setId(3L);
        when(scheduleRepo.findByParentScheduleId(1L)).thenReturn(List.of(child));
        when(scheduleRepo.findByParentScheduleId(2L)).thenReturn(List.of(grandchild));
        when(scheduleRepo.findByParentScheduleId(3L)).thenReturn(List.of());

        service.purgeScheduleTree(1L);

        verify(sessionRepo).deleteByScheduleId(1L);
        verify(sessionRepo).deleteByScheduleId(2L);
        verify(sessionRepo).deleteByScheduleId(3L);
        verify(preAllocationRepo).deleteByScheduleId(1L);
        verify(preAllocationRepo).deleteByScheduleId(2L);
        verify(preAllocationRepo).deleteByScheduleId(3L);
        verify(scheduleRepo).deleteAllByIdInBatch(List.of(1L, 2L, 3L));
    }

    @Test
    void purgePreAllocationsDelegatesByParentType() {
        service.purgePreAllocationsForBatch(10L);
        verify(preAllocationRepo).deleteByBatchId(10L);

        service.purgePreAllocationsForSubject(11L);
        verify(preAllocationRepo).deleteBySubjectId(11L);

        service.purgePreAllocationsForTeacher(12L);
        verify(preAllocationRepo).deleteByTeacherId(12L);

        service.purgePreAllocationsForRoom(13L);
        verify(preAllocationRepo).deleteByRoomId(13L);

        service.purgePreAllocationsForTimeslot(14L);
        verify(preAllocationRepo).deleteByTimeslotId(14L);

        service.purgePreAllocationsForDepartment(15L);
        verify(preAllocationRepo).deleteByDepartmentId(15L);
    }

    @Test
    void detachTimeslotRemovesAvailabilityAndEventJoinRows() {
        service.detachTimeslot(7L);

        verify(teacherRepo).deleteTeacherAvailabilityByTimeslotId(7L);
        verify(roomRepo).deleteRoomAvailabilityByTimeslotId(7L);
        verify(eventRepo).deleteEventAffectedTimeslotsByTimeslotId(7L);
    }

    @Test
    void detachRoomAndTeacherFromEvents() {
        service.detachRoomFromEvents(5L);
        verify(eventRepo).deleteEventAffectedRoomsByRoomId(5L);

        service.detachTeacherFromEvents(6L);
        verify(eventRepo).deleteEventAffectedTeachersByTeacherId(6L);
    }
}