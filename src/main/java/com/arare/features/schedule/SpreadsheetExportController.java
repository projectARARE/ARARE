package com.arare.features.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Generic spreadsheet export for master-data tables. The frontend renders any
// data table to plain columns/rows and asks for an .xlsx here, so CSV + Excel
// export works on every page without per-entity backend endpoints.
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class SpreadsheetExportController {

    private final ExcelExportService excelExportService;

    public record ExportRequest(
            String sheetName,
            @NotNull @NotEmpty List<@NotEmpty String> headers,
            List<List<String>> rows
    ) {}

    @PostMapping(value = "/excel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportExcel(@Valid @RequestBody ExportRequest req) {
        byte[] excel = excelExportService.exportRows(
                req.sheetName(),
                req.headers(),
                req.rows() == null ? List.of() : req.rows());
        return ResponseEntity.ok()
            .header("Content-Disposition",
                    "attachment; filename=\"" + safeFilename(req.sheetName()) + ".xlsx\"")
            .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excel);
    }

    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) return "export";
        return name.replaceAll("[^a-zA-Z0-9 _-]", "").trim().replaceAll("\\s+", "_");
    }
}