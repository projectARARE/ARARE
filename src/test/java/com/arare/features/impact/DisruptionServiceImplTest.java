package com.arare.features.impact;

import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.solvejob.SolveJobResponse;
import com.arare.features.solvejob.SolveJobService;
import com.arare.features.solvejob.SolveJobStatus;
import com.arare.features.solvejob.SolveJobType;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisruptionServiceImplTest {

    @Mock private ScheduleRepository scheduleRepo;
    @Mock private ClassSessionRepository sessionRepo;
    @Mock private DependencyGraphBuilder graphBuilder;
    @Mock private ImpactAnalyzer impactAnalyzer;
    @Mock private SolveJobService solveJobService;
    @Mock private TeacherRepository teacherRepo;
    @Mock private RoomRepository roomRepo;
    @Mock private TimeslotRepository timeslotRepo;

    @InjectMocks
    private DisruptionServiceImpl service;

    private static SolveJobResponse job() {
        return new SolveJobResponse(9L, SolveJobType.PARTIAL_RESOLVE, 1L, SolveJobStatus.QUEUED,
            null, null, null, null, null, null, null);
    }

    @Test
    void applyDisruption_withImpactedSessions_submitsPartialResolve() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of());
        when(graphBuilder.build(any())).thenReturn(new DependencyGraph());
        when(impactAnalyzer.analyze(any(), any(), any())).thenReturn(Set.of(10L, 11L));
        when(solveJobService.submitPartialResolve(eq(1L), anyList())).thenReturn(job());

        DisruptionRequest request = new DisruptionRequest(
            DisruptionType.SPECIAL_EVENT, null, LocalDate.of(2026, 8, 10), "Assembly");

        SolveJobResponse response = service.applyDisruption(1L, request);

        assertEquals(1L, response.scheduleId());
        verify(solveJobService).submitPartialResolve(eq(1L),
            argThat(ids -> new HashSet<>(ids).equals(Set.of(10L, 11L))));
        verify(solveJobService, never()).completedNoop(any());
    }

    @Test
    void applyDisruption_withNoImpactedSessions_returnsNoop() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of());
        when(graphBuilder.build(any())).thenReturn(new DependencyGraph());
        when(impactAnalyzer.analyze(any(), any(), any())).thenReturn(Set.of());
        when(solveJobService.completedNoop(1L)).thenReturn(job());

        DisruptionRequest request = new DisruptionRequest(
            DisruptionType.SPECIAL_EVENT, null, LocalDate.of(2026, 8, 10), "Assembly");

        SolveJobResponse response = service.applyDisruption(1L, request);

        assertEquals(1L, response.scheduleId());
        verify(solveJobService).completedNoop(1L);
        verify(solveJobService, never()).submitPartialResolve(any(), anyList());
    }

    @Test
    void applyDisruption_requiresEntityIdForNonSpecialEvents() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));

        DisruptionRequest request = new DisruptionRequest(
            DisruptionType.TEACHER_UNAVAILABLE, null, null, "Absent");

        assertThrows(IllegalArgumentException.class, () -> service.applyDisruption(1L, request));
    }
}
