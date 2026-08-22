package com.arare.features.solvejob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SolveJobRepository extends JpaRepository<SolveJob, Long> {

    List<SolveJob> findAllByOrderByCreatedAtDesc();

    List<SolveJob> findByStatusOrderByCreatedAtDesc(SolveJobStatus status);

    List<SolveJob> findByStatusIn(Collection<SolveJobStatus> statuses);

    List<SolveJob> findByScheduleIdOrderByCreatedAtDesc(Long scheduleId);

    Optional<SolveJob> findTopByScheduleIdAndJobTypeOrderByCreatedAtDesc(Long scheduleId, SolveJobType jobType);

    long countByScheduleIdAndStatusIn(Long scheduleId, Collection<SolveJobStatus> statuses);

    /**
     * Atomic status transition guarded on the current status. Returns the
     * number of rows changed (1 when the transition won, 0 when the job had
     * already left {@code fromStatuses} — e.g. cancelled while the runner was
     * persisting). Used to close the cancel/persist race so a cancelled job
     * can never be overwritten by a late SUCCEEDED write, and vice versa.
     *
     * <p>Self-transactional: the runner calls this both inside an explicit
     * {@code transactionTemplate} (success path) and directly from the async
     * solver thread (failure path), which has no surrounding transaction — a
     * {@code @Modifying} query without one fails with
     * {@code TransactionRequiredException}.</p>
     */
    @Transactional
    @Modifying
    @Query("""
        UPDATE SolveJob j
        SET j.status = :toStatus,
            j.score = :score,
            j.elapsedMillis = :elapsedMillis,
            j.finishedAt = :finishedAt,
            j.errorMessage = :errorMessage
        WHERE j.id = :id AND j.status IN :fromStatuses
        """)
    int transitionTerminal(@Param("id") Long id,
        @Param("fromStatuses") Collection<SolveJobStatus> fromStatuses,
        @Param("toStatus") SolveJobStatus toStatus,
        @Param("score") String score,
        @Param("elapsedMillis") Long elapsedMillis,
        @Param("finishedAt") LocalDateTime finishedAt,
        @Param("errorMessage") String errorMessage);

    /**
     * Best-score telemetry update that only applies while the job is still
     * QUEUED or RUNNING. Unlike merging the whole detached entity (which would
     * also write back a stale status field), this can never resurrect a job the
     * operator has already cancelled or marked failed.
     *
     * <p>Self-transactional for the same reason as {@link #transitionTerminal}:
     * it runs on the async solver thread with no outer transaction.</p>
     */
    @Transactional
    @Modifying
    @Query("""
        UPDATE SolveJob j
        SET j.bestScore = :bestScore
        WHERE j.id = :id AND j.status IN (com.arare.features.solvejob.SolveJobStatus.QUEUED, com.arare.features.solvejob.SolveJobStatus.RUNNING)
        """)
    int updateBestScoreIfActive(@Param("id") Long id, @Param("bestScore") String bestScore);
}
