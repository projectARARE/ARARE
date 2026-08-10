package com.arare.features.solvejob;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolveJobRecoverySweeperTest {

    @Mock private SolveJobRepository jobRepo;

    @InjectMocks
    private SolveJobRecoverySweeper sweeper;

    private SolveJob job(long id, SolveJobStatus status) {
        SolveJob job = SolveJob.builder()
            .jobType(SolveJobType.GENERATE)
            .scheduleId(1L)
            .status(status)
            .build();
        job.setId(id);
        return job;
    }

    @Test
    void sweepStaleJobsOnStartup_marksQueuedAndRunningJobsFailed() {
        SolveJob running = job(1L, SolveJobStatus.RUNNING);
        SolveJob queued = job(2L, SolveJobStatus.QUEUED);
        when(jobRepo.findByStatusIn(List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING)))
            .thenReturn(List.of(running, queued));

        sweeper.sweepStaleJobsOnStartup();

        assertEquals(SolveJobStatus.FAILED, running.getStatus());
        assertEquals(SolveJobStatus.FAILED, queued.getStatus());
        assertEquals("Interrupted by a restart — the job was in RUNNING when the application shut down",
            running.getErrorMessage());
        assertEquals("Interrupted by a restart — the job was in QUEUED when the application shut down",
            queued.getErrorMessage());
        verify(jobRepo).saveAll(any());
    }

    @Test
    void sweepStaleJobsOnStartup_noopWhenNothingStale() {
        when(jobRepo.findByStatusIn(List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING)))
            .thenReturn(List.of());

        sweeper.sweepStaleJobsOnStartup();

        verify(jobRepo, never()).saveAll(any());
    }
}