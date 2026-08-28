package com.arare.features.solvejob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;

import com.arare.features.schedule.ScheduleRequest;
import com.arare.features.solver.DisruptionConstraintFact;

import com.arare.features.impact.DisruptionType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolveJobServiceTest {

    @Mock private SolveJobRepository jobRepo;
    @Mock private SolveJobRunner runner;
    @Mock private ActiveSolverRegistry solverRegistry;

    @InjectMocks
    private SolveJobService service;

    @Test
    void submitGenerate_persistsQueuedJobAndStartsWorkerWhenNoTransaction() {
        SolveJob saved = SolveJob.builder()
            .jobType(SolveJobType.GENERATE)
            .scheduleId(1L)
            .status(SolveJobStatus.QUEUED)
            .build();
        saved.setId(7L);
        when(jobRepo.save(any(SolveJob.class))).thenReturn(saved);

        try (MockedStatic<org.springframework.transaction.support.TransactionSynchronizationManager> mocked =
                 mockStatic(org.springframework.transaction.support.TransactionSynchronizationManager.class)) {
            mocked.when(org.springframework.transaction.support.TransactionSynchronizationManager::isActualTransactionActive)
                .thenReturn(false);

            ScheduleRequest req = new ScheduleRequest(
                "Test", null, null, 1L, null, List.of(2L, 3L), null, null, 30, null, null);

            SolveJobResponse response = service.submitGenerate(1L, req);

            assertEquals(7L, response.id());
            assertEquals(SolveJobStatus.QUEUED, response.status());
            assertEquals(SolveJobType.GENERATE, response.jobType());
            verify(runner).run(7L);

            ArgumentCaptor<SolveJob> captor = ArgumentCaptor.forClass(SolveJob.class);
            verify(jobRepo).save(captor.capture());
            assertEquals("2,3", captor.getValue().getBatchIdsCsv());
            assertEquals(30, captor.getValue().getSolvingTimeSeconds());
        }
    }

    @Test
    void submitPartialResolve_persistsImpactedIds() {
        SolveJob saved = SolveJob.builder()
            .jobType(SolveJobType.PARTIAL_RESOLVE)
            .scheduleId(5L)
            .status(SolveJobStatus.QUEUED)
            .build();
        saved.setId(9L);
        when(jobRepo.save(any(SolveJob.class))).thenReturn(saved);

        try (MockedStatic<org.springframework.transaction.support.TransactionSynchronizationManager> mocked =
                 mockStatic(org.springframework.transaction.support.TransactionSynchronizationManager.class)) {
            mocked.when(org.springframework.transaction.support.TransactionSynchronizationManager::isActualTransactionActive)
                .thenReturn(false);

            SolveJobResponse response = service.submitPartialResolve(5L, List.of(11L, 22L));

            assertEquals(9L, response.id());
            verify(runner).run(9L);

            ArgumentCaptor<SolveJob> captor = ArgumentCaptor.forClass(SolveJob.class);
            verify(jobRepo).save(captor.capture());
            assertEquals("11,22", captor.getValue().getImpactedSessionIdsCsv());
        }
    }

    @Test
    void submitPartialResolve_persistsDisruptionFacts() {
        SolveJob saved = SolveJob.builder()
            .jobType(SolveJobType.PARTIAL_RESOLVE)
            .scheduleId(5L)
            .status(SolveJobStatus.QUEUED)
            .build();
        saved.setId(10L);
        when(jobRepo.save(any(SolveJob.class))).thenReturn(saved);

        try (MockedStatic<org.springframework.transaction.support.TransactionSynchronizationManager> mocked =
                 mockStatic(org.springframework.transaction.support.TransactionSynchronizationManager.class)) {
            mocked.when(org.springframework.transaction.support.TransactionSynchronizationManager::isActualTransactionActive)
                .thenReturn(false);

            List<DisruptionConstraintFact> facts = List.of(
                new DisruptionConstraintFact(DisruptionType.TEACHER_UNAVAILABLE, 3L, "MONDAY"),
                new DisruptionConstraintFact(DisruptionType.SPECIAL_EVENT, null, "FRIDAY"));

            SolveJobResponse response = service.submitPartialResolve(5L, List.of(11L), facts);

            assertEquals(10L, response.id());
            verify(runner).run(10L);

            ArgumentCaptor<SolveJob> captor = ArgumentCaptor.forClass(SolveJob.class);
            verify(jobRepo).save(captor.capture());
            assertEquals("TEACHER_UNAVAILABLE:3:MONDAY;SPECIAL_EVENT::FRIDAY",
                captor.getValue().getDisruptionFactsCsv());
        }
    }

    @Test
    void submitPartialResolve_withoutFacts_leavesDisruptionFactsNull() {
        SolveJob saved = SolveJob.builder()
            .jobType(SolveJobType.PARTIAL_RESOLVE)
            .scheduleId(5L)
            .status(SolveJobStatus.QUEUED)
            .build();
        saved.setId(11L);
        when(jobRepo.save(any(SolveJob.class))).thenReturn(saved);

        try (MockedStatic<org.springframework.transaction.support.TransactionSynchronizationManager> mocked =
                 mockStatic(org.springframework.transaction.support.TransactionSynchronizationManager.class)) {
            mocked.when(org.springframework.transaction.support.TransactionSynchronizationManager::isActualTransactionActive)
                .thenReturn(false);

            service.submitPartialResolve(5L, List.of(11L));

            ArgumentCaptor<SolveJob> captor = ArgumentCaptor.forClass(SolveJob.class);
            verify(jobRepo).save(captor.capture());
            assertEquals(null, captor.getValue().getDisruptionFactsCsv());
        }
    }

    @Test
    void completedNoop_returnsSyntheticSucceededResponseWithoutJobRow() {
        SolveJobResponse response = service.completedNoop(5L);

        assertNull(response.id());
        assertEquals(5L, response.scheduleId());
        assertEquals(SolveJobStatus.SUCCEEDED, response.status());
        verify(jobRepo, never()).save(any(SolveJob.class));
        verify(runner, never()).run(any(Long.class));
    }

    @Test
    void cancelQueuedJob_marksCancelled() {
        SolveJob job = SolveJob.builder()
            .jobType(SolveJobType.GENERATE)
            .scheduleId(1L)
            .status(SolveJobStatus.QUEUED)
            .build();
        job.setId(3L);
        when(jobRepo.transitionTerminal(eq(3L), anyCollection(), eq(SolveJobStatus.CANCELLED), any(), any(), any(), any(), any()))
            .thenReturn(1);
        SolveJob cancelled = SolveJob.builder()
            .jobType(SolveJobType.GENERATE)
            .scheduleId(1L)
            .status(SolveJobStatus.CANCELLED)
            .build();
        cancelled.setId(3L);
        when(jobRepo.findById(3L)).thenReturn(java.util.Optional.of(job)).thenReturn(java.util.Optional.of(cancelled));

        SolveJobResponse response = service.cancel(3L);

        assertEquals(SolveJobStatus.CANCELLED, response.status());
        verify(runner, never()).run(any(Long.class));
    }

    @Test
    void cancelQueuedJob_lostRaceThrowsConflict() {
        SolveJob job = SolveJob.builder()
            .jobType(SolveJobType.GENERATE)
            .scheduleId(1L)
            .status(SolveJobStatus.RUNNING)
            .build();
        job.setId(3L);
        when(jobRepo.findById(3L)).thenReturn(java.util.Optional.of(job));
        when(jobRepo.transitionTerminal(eq(3L), anyCollection(), eq(SolveJobStatus.CANCELLED), any(), any(), any(), any(), any()))
            .thenReturn(0);

        assertThrows(com.arare.exception.ResourceConflictException.class, () -> service.cancel(3L));
    }

    @Test
    void cancelTerminalJob_throws() {
        SolveJob job = SolveJob.builder()
            .jobType(SolveJobType.GENERATE)
            .scheduleId(1L)
            .status(SolveJobStatus.SUCCEEDED)
            .build();
        job.setId(4L);
        when(jobRepo.findById(4L)).thenReturn(java.util.Optional.of(job));

        assertThrows(com.arare.exception.ResourceConflictException.class, () -> service.cancel(4L));
    }

    @Test
    void ensureNoActiveJobForSchedule_rejectsWhenSolveInProgress() {
        when(jobRepo.countByScheduleIdAndStatusIn(1L, List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING)))
            .thenReturn(2L);

        assertThrows(com.arare.exception.ResourceBusyException.class,
            () -> service.ensureNoActiveJobForSchedule(1L));
    }

    @Test
    void ensureNoActiveJobForSchedule_passesWhenNoSolveInProgress() {
        when(jobRepo.countByScheduleIdAndStatusIn(1L, List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING)))
            .thenReturn(0L);

        service.ensureNoActiveJobForSchedule(1L);
    }
}
