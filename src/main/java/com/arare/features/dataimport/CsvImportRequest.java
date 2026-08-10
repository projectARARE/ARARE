package com.arare.features.dataimport;

import jakarta.validation.constraints.NotBlank;

public record CsvImportRequest(@NotBlank String csvContent, boolean dryRun) {

    public CsvImportRequest(String csvContent) {
        this(csvContent, false);
    }
}
