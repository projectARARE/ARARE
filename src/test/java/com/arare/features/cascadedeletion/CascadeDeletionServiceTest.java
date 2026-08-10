package com.arare.features.cascadedeletion;

import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.preallocation.PreAllocationRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CascadeDeletionServiceTest {

    @Mock
    private PreAllocationRepository preAllocationRepo;

    @Mock
    private ClassSessionRepository sessionRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CascadeDeletionService service;

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
        Query q1 = org.mockito.Mockito.mock(Query.class);
        Query q2 = org.mockito.Mockito.mock(Query.class);
        Query q3 = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createNativeQuery("DELETE FROM teacher_availability WHERE timeslot_id = :id")).thenReturn(q1);
        when(entityManager.createNativeQuery("DELETE FROM room_availability WHERE timeslot_id = :id")).thenReturn(q2);
        when(entityManager.createNativeQuery("DELETE FROM event_affected_timeslots WHERE timeslot_id = :id")).thenReturn(q3);
        when(q1.setParameter("id", 7L)).thenReturn(q1);
        when(q2.setParameter("id", 7L)).thenReturn(q2);
        when(q3.setParameter("id", 7L)).thenReturn(q3);

        service.detachTimeslot(7L);

        verify(q1).executeUpdate();
        verify(q2).executeUpdate();
        verify(q3).executeUpdate();
    }

    @Test
    void detachRoomAndTeacherFromEvents() {
        Query qRoom = org.mockito.Mockito.mock(Query.class);
        Query qTeacher = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createNativeQuery("DELETE FROM event_affected_rooms WHERE room_id = :id")).thenReturn(qRoom);
        when(entityManager.createNativeQuery("DELETE FROM event_affected_teachers WHERE teacher_id = :id")).thenReturn(qTeacher);
        when(qRoom.setParameter("id", 5L)).thenReturn(qRoom);
        when(qTeacher.setParameter("id", 6L)).thenReturn(qTeacher);

        service.detachRoomFromEvents(5L);
        service.detachTeacherFromEvents(6L);

        verify(qRoom).executeUpdate();
        verify(qTeacher).executeUpdate();
    }
}
