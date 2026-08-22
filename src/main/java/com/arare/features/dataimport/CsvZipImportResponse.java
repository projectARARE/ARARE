package com.arare.features.dataimport;

import java.util.List;
import java.util.Map;

public record CsvZipImportResponse(
    Map<String, FileImportStats> fileStats,
    List<String> globalErrors,
    boolean dryRun
) {
    public CsvZipImportResponse(Map<String, FileImportStats> fileStats, List<String> globalErrors) {
        this(fileStats, globalErrors, false);
    }

    public record FileImportStats(
        String fileName,
        int created,
        int updated,
        int skipped,
        List<String> errors
    ) {}
}
