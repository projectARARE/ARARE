package com.arare.features.dataimport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CsvImportRequest(@NotBlank @Size(max = 10_000_000) String csvContent, boolean dryRun) {

    public CsvImportRequest(String csvContent) {
        this(csvContent, false);
    }
}
