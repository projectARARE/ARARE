package com.arare.features.schedule;

public record ConflictSuggestionResponse(
    Long timeslotId,
    String label,
    String preview,
    String scoreHint,
    int hardConflicts,
    int softPenalties
) {
}
