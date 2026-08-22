package com.arare.features.solvejob;

import ai.timefold.solver.core.api.solver.Solver;
import com.arare.exception.ResourceBusyException;
import com.arare.exception.ResourceConflictException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.schedule.ScheduleRequest;
import com.arare.features.solver.DisruptionConstraintFact;
import com.arare.features.solver.TimetableSolution;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orchestrates the durable solve-job lifecycle.
 *
 * <p>Submission only persists a {@link SolveJob} row (short transaction, or
 * the caller's transaction) and then schedules the worker. The worker runs on
 * the {@code solveTaskExecutor} and persists status transitions itself, so no
 * transaction is ever held open across a solver run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolveJobService {

    private final SolveJobRepository jobRepo;
    private final SolveJobRunner runner;
    private final ActiveSolverRegistry solverRegistry;

    /**
     * Creates a GENERATE job for a new schedule. Must be called from within a
     * transaction (the schedule row is created there); the worker is started
     * only after that transaction commits so it can always see its job row.
     */
    public SolveJobResponse submitGenerate(Long scheduleId, ScheduleRequest req) {
        SolveJob job = SolveJob.builder()
            .jobType(SolveJobType.GENERATE)
            .scheduleId(scheduleId)
            .status(SolveJobStatus.QUEUED)
            .solvingTimeSeconds(req.solvingTimeSeconds())
            .departmentId(req.departmentId())
            .instituteId(req.instituteId())
            .batchIdsCsv(join(req.batchIds()))
            .teacherIdsCsv(join(req.teacherIds()))
            .roomIdsCsv(join(req.roomIds()))
            .build();
        job = jobRepo.save(job);
        scheduleWorkerAfterCommit(job.getId());
        return toResponse(job);
    }

    /**
     * Creates a PARTIAL_RESOLVE job for an existing schedule.
     */
    public SolveJobResponse submitPartialResolve(Long scheduleId, List<Long> impactedSessionIds) {
        return submitPartialResolve(scheduleId, impactedSessionIds, null);
    }

    /**
     * Creates a PARTIAL_RESOLVE job for an existing schedule, carrying the
     * disruption facts the solver must enforce while repairing the schedule.
     */
    public SolveJobResponse submitPartialResolve(
            Long scheduleId, List<Long> impactedSessionIds, List<DisruptionConstraintFact> disruptionFacts) {
        SolveJob job = SolveJob.builder()
            .jobType(SolveJobType.PARTIAL_RESOLVE)
            .scheduleId(scheduleId)
            .status(SolveJobStatus.QUEUED)
            .impactedSessionIdsCsv(join(impactedSessionIds))
            .disruptionFactsCsv(DisruptionConstraintFact.encode(disruptionFacts))
            .build();
        job = jobRepo.save(job);
        scheduleWorkerAfterCommit(job.getId());
        return toResponse(job);
    }

    public SolveJobResponse get(Long jobId) {
        return toResponse(find(jobId));
    }

    public List<SolveJobResponse> list(SolveJobStatus status) {
        List<SolveJob> jobs = status != null
            ? jobRepo.findByStatusOrderByCreatedAtDesc(status)
            : jobRepo.findAllByOrderByCreatedAtDesc();
        return jobs.stream().map(this::toResponse).toList();
    }

    public List<SolveJobResponse> listForSchedule(Long scheduleId) {
        return jobRepo.findByScheduleIdOrderByCreatedAtDesc(scheduleId)
            .stream().map(this::toResponse).toList();
    }

    /**
     * Rejects destructive operations on a schedule while a solve job for it
     * is still QUEUED or RUNNING, so an in-flight worker can never write into
     * (or throw against) a schedule tree that is being torn down.
     */
    public void ensureNoActiveJobForSchedule(Long scheduleId) {
        long active = jobRepo.countByScheduleIdAndStatusIn(
            scheduleId, List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING));
        if (active > 0) {
            throw new ResourceBusyException(
                "Schedule " + scheduleId + " has " + active
                    + " solve job(s) in progress — wait for them to finish before deleting it");
        }
    }

    public SolveJobResponse cancel(Long jobId) {
        SolveJob job = find(jobId);
        if (job.getStatus() == SolveJobStatus.QUEUED || job.getStatus() == SolveJobStatus.RUNNING) {
            if (job.getStatus() == SolveJobStatus.RUNNING && job.getProblemId() != null) {
                Solver<TimetableSolution> solver = solverRegistry.get(job.getProblemId());
                if (solver != null) {
                    solver.terminateEarly();
                }
            }
            int updated = jobRepo.transitionTerminal(
                jobId,
                List.of(SolveJobStatus.QUEUED, SolveJobStatus.RUNNING),
                SolveJobStatus.CANCELLED,
                null,
                null,
                java.time.LocalDateTime.now(),
                null);
            if (updated == 0) {
                // The job left QUEUED/RUNNING between our read and the update
                // (e.g. the runner just finished). Report it as not cancellable.
                throw new ResourceConflictException(
                    "Job " + jobId + " is not cancellable in status " + job.getStatus());
            }
            job = find(jobId);
        } else {
            throw new ResourceConflictException(
                "Job " + jobId + " is not cancellable in status " + job.getStatus());
        }
        return toResponse(job);
    }

    /**
     * Synthetic terminal response used when an operation completes without
     * requiring a solver run (e.g. a disruption that impacts no sessions).
     */
    public SolveJobResponse completedNoop(Long scheduleId) {
        return new SolveJobResponse(
            null, SolveJobType.PARTIAL_RESOLVE, scheduleId, SolveJobStatus.SUCCEEDED,
            null, null, null, 0L, null, null, null);
    }

    /**
     * Runs the worker only after the current transaction has committed, so the
     * async thread never races an uncommitted job row. Falls back to immediate
     * execution when no transaction is active (defensive; all callers are
     * transactional).
     */
    private void scheduleWorkerAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        runner.run(jobId);
                    }
                });
        } else {
            runner.run(jobId);
        }
    }

    private SolveJob find(Long jobId) {
        return jobRepo.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("SolveJob", jobId));
    }

    private static String join(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
    }

    private SolveJobResponse toResponse(SolveJob job) {
        return new SolveJobResponse(
            job.getId(),
            job.getJobType(),
            job.getScheduleId(),
            job.getStatus(),
            job.getScore(),
            job.getBestScore(),
            job.getErrorMessage(),
            job.getElapsedMillis(),
            job.getCreatedAt() != null ? job.getCreatedAt().toString() : null,
            job.getStartedAt() != null ? job.getStartedAt().toString() : null,
            job.getFinishedAt() != null ? job.getFinishedAt().toString() : null
        );
    }
}
