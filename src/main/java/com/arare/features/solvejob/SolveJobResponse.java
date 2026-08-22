package com.arare.features.solvejob;

public record SolveJobResponse(
    Long id,
    SolveJobType jobType,
    Long scheduleId,
    SolveJobStatus status,
    String score,
    String bestScore,
    String errorMessage,
    Long elapsedMillis,
    String createdAt,
    String startedAt,
    String finishedAt
) {

    public boolean isTerminal() {
        return status == SolveJobStatus.SUCCEEDED
            || status == SolveJobStatus.FAILED
            || status == SolveJobStatus.CANCELLED;
    }
}
