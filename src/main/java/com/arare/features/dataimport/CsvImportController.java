package com.arare.features.dataimport;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * Import / export endpoints. Two import paths are supported:
 *
 * <ul>
 *   <li>Single-entity CSV ({@code POST /import/csv/{entityType}}) — one
 *       entity kind per file, its dependencies must already exist.</li>
 *   <li>Relational ZIP ({@code POST /import/zip}) — the full export package;
 *       processed in dependency order and safe against missing files.</li>
 * </ul>
 *
 * Both share the same upsert logic, natural keys, and partial-update semantics.
 */
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final RelationalCsvImportService relationalCsvImportService;
    private final RelationalCsvExportService relationalCsvExportService;
    private final CsvTemplateService csvTemplateService;

    @PostMapping("/csv/{entityType}")
    public ResponseEntity<CsvImportResponse> importCsv(
        @PathVariable String entityType,
        @Valid @RequestBody CsvImportRequest request
    ) {
        return ResponseEntity.ok(csvImportService.importCsv(entityType, request.csvContent(), request.dryRun()));
    }

    @PostMapping(value = "/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvZipImportResponse> importZip(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun
    ) {
        return ResponseEntity.ok(relationalCsvImportService.importZip(file, dryRun));
    }

    @GetMapping(value = "/export/zip", produces = "application/zip")
    public ResponseEntity<byte[]> exportZip() {
        byte[] zipData = relationalCsvExportService.exportZip();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"arare-export.zip\"")
            .body(zipData);
    }

    @GetMapping(value = "/export/csv/{entityType}", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String entityType) {
        CsvEntityType type = CsvEntityType.fromName(entityType);
        String csv = relationalCsvExportService.exportCsv(type);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + type.getFileName() + "\"")
            .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/template/csv/{entityType}", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportTemplateCsv(@PathVariable String entityType) {
        CsvEntityType type = CsvEntityType.fromName(entityType);
        String csv = csvTemplateService.exportTemplateCsv(type);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csvTemplateService.templateFileName(type) + "\"")
            .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Full set of example templates (all entities + relationship pairing files). */
    @GetMapping(value = "/template/zip", produces = "application/zip")
    public ResponseEntity<byte[]> exportTemplateZip() {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos,
                 java.nio.charset.StandardCharsets.UTF_8)) {
            for (CsvEntityType type : CsvEntityType.importOrder()) {
                writeTemplateEntry(zos, csvTemplateService.templateFileName(type),
                    csvTemplateService.exportTemplateCsv(type));
            }
            for (String filename : csvTemplateService.relationshipFileNames()) {
                String csv = csvTemplateService.relationshipTemplate(filename);
                if (csv != null && !csv.isBlank()) {
                    writeTemplateEntry(zos, filename.replace(".csv", "-template.csv"), csv);
                }
            }
            zos.finish();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"arare-csv-templates.zip\"")
                .body(baos.toByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to build template ZIP", e);
        }
    }

    private static void writeTemplateEntry(java.util.zip.ZipOutputStream zos, String filename, String content)
            throws java.io.IOException {
        zos.putNextEntry(new java.util.zip.ZipEntry(filename));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /** Canonical dependency-ordered import sequence for UI consumption. */
    @GetMapping("/order")
    public ResponseEntity<List<ImportOrderStep>> importOrder() {
        List<ImportOrderStep> steps = Arrays.stream(CsvEntityType.values())
            .map(type -> new ImportOrderStep(
                type.getName(),
                type.getDisplayName(),
                type.getFileName(),
                type.getDependencies().stream().map(CsvEntityType::getName).toList()))
            .toList();
        return ResponseEntity.ok(steps);
    }
}