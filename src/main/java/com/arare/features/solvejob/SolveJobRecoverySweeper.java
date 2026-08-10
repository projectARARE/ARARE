package com.arare.features.solvejob;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup recovery for solve jobs left non-terminal by a crash or a hard
 * shutdown. A job row in QUEUED or RUNNING after a restart can never be
 * picked up again (workers are scheduled in-process after commit), so it is
 * marked FAILED with an explicit message instead of remaining RUNNING
 * forever and blocking schedule deletion via
 * {@link SolveJobService#ensureNoActiveJobForSchedule}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolveJobRecoverySweeper {

    private final SolveJobRepository jobRepo;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sweepStaleJobsOnStartup() {
        List<SolveJob> stale = jobRepo.findByStatusIn(List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING));
        if (stale.isEmpty()) {
            return;
        }
        for (SolveJob job : stale) {
            SolveJobStatus previous = job.getStatus();
            job.setStatus(SolveJobStatus.FAILED);
            job.setErrorMessage("Interrupted by a restart — the job was in " + previous
                + " when the application shut down");
            job.setFinishedAt(LocalDateTime.now());
            log.warn("Recovered stale solve job {} (was {}) → FAILED", job.getId(), previous);
        }
        jobRepo.saveAll(stale);
    }
}