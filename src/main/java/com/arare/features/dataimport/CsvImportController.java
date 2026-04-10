package com.arare.features.dataimport;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/import/csv")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;

    @PostMapping("/{entityType}")
    public ResponseEntity<CsvImportResponse> importCsv(
        @PathVariable String entityType,
        @Valid @RequestBody CsvImportRequest request
    ) {
        return ResponseEntity.ok(csvImportService.importCsv(entityType, request.csvContent()));
    }
}
