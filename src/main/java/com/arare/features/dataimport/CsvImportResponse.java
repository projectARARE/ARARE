package com.arare.features.dataimport;

import java.util.List;

public record CsvImportResponse(
    String entityType,
    String displayName,
    int created,
    int updated,
    int skipped,
    List<String> errors,
    boolean dryRun
) {
    public CsvImportResponse(String entityType, int created, int updated, int skipped, List<String> errors) {
        this(entityType, CsvEntityType.fromName(entityType).getDisplayName(), created, updated, skipped, errors, false);
    }

    public CsvImportResponse(String entityType, int created, int updated, int skipped, List<String> errors, boolean dryRun) {
        this(entityType, CsvEntityType.fromName(entityType).getDisplayName(), created, updated, skipped, errors, dryRun);
    }
}
