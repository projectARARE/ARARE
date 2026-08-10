package com.arare.features.solvejob;

import com.arare.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Durable record of a solve request.
 *
 * <p>Solve requests are decoupled from the HTTP request that created them:
 * the API returns the job id immediately and the worker persists status
 * transitions (QUEUED → RUNNING → SUCCEEDED / FAILED / CANCELLED) in short
 * transactions. The request snapshot columns (departmentId, id lists, solving
 * time) allow a job to be rebuilt or retried without losing the original
 * intent, and the {@code problemId} column tracks the underlying Timefold
 * problem so cancellation can reach the live solver.
 */
@Entity
@Table(name = "solve_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolveJob extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SolveJobType jobType;

    @Column(nullable = false)
    private Long scheduleId;

    @Column
    private UUID problemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SolveJobStatus status;

    // ── Request snapshot (GENERATE) ─────────────────────────────────────────
    @Column
    private Integer solvingTimeSeconds;

    @Column
    private Long departmentId;

    @Column(columnDefinition = "TEXT")
    private String batchIdsCsv;

    @Column(columnDefinition = "TEXT")
    private String teacherIdsCsv;

    @Column(columnDefinition = "TEXT")
    private String roomIdsCsv;

    // ── Request snapshot (PARTIAL_RESOLVE) ──────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String impactedSessionIdsCsv;

    // ── Outcome ─────────────────────────────────────────────────────────────
    @Column
    private String score;

    @Column
    private String bestScore;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private Long elapsedMillis;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime finishedAt;
}
