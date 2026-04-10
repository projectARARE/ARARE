package com.arare.features.dataimport;

import java.util.List;

public record CsvImportResponse(
    String entityType,
    int created,
    int updated,
    int skipped,
    List<String> errors
) {
}
