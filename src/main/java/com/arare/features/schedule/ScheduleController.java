package com.arare.features.schedule;

import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.impact.DisruptionRequest;
import com.arare.features.impact.DisruptionResponse;
import com.arare.features.impact.DisruptionService;
import com.arare.features.solver.ScoreExplanationResponse;
import com.arare.features.solvejob.SolveJobResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService          service;
    private final DisruptionService        disruptionService;
    private final TimetableExportService   exportService;
    private final PdfExportService         pdfExportService;
    private final ExcelExportService       excelExportService;
    private final FeasibilityCheckService  feasibilityCheckService;

    @PostMapping("/generate")
    public ResponseEntity<SolveJobResponse> generate(@Valid @RequestBody ScheduleRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.generate(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ScheduleResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(service.activate(id));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ScheduleResponse> archive(@PathVariable Long id) {
        return ResponseEntity.ok(service.archive(id));
    }

    @PostMapping("/{id}/partial-resolve")
    public ResponseEntity<SolveJobResponse> partialResolve(
        @PathVariable Long id,
        @Valid @RequestBody PartialResolveRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(service.partialResolve(id, request.impactedSessionIds()));
    }

    @GetMapping("/{id}/score-explanation")
    public ResponseEntity<ScoreExplanationResponse> scoreExplanation(@PathVariable Long id) {
        return ResponseEntity.ok(service.explainScore(id));
    }

    @GetMapping("/{id}/explanation")
    public ResponseEntity<String> getExplanation(@PathVariable Long id) {
        return ResponseEntity.ok(service.getExplanation(id));
    }

    @GetMapping("/{id}/sessions/{sessionId}/suggestions")
    public ResponseEntity<List<ConflictSuggestionResponse>> suggestFixes(
            @PathVariable Long id,
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "4") int limit) {
        return ResponseEntity.ok(service.suggestFixes(id, sessionId, limit));
    }

    @GetMapping("/{id}/sessions")
    public ResponseEntity<List<ClassSessionResponse>> getSessions(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSessionsBySchedule(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disruption/preview")
    public ResponseEntity<DisruptionResponse> previewDisruption(
            @PathVariable Long id,
            @Valid @RequestBody DisruptionRequest request) {
        return ResponseEntity.ok(disruptionService.previewImpact(id, request));
    }

    @PostMapping("/{id}/disruption/apply")
    public ResponseEntity<SolveJobResponse> applyDisruption(
            @PathVariable Long id,
            @Valid @RequestBody DisruptionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(disruptionService.applyDisruption(id, request));
    }

    @GetMapping("/{id}/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ALL") TimetableExportService.View view,
            @RequestParam(required = false) Long entityId) {
        byte[] csv = exportService.exportCsv(id, view, entityId);
        boolean zip = (view != TimetableExportService.View.ALL) && entityId == null;
        ResponseEntity.BodyBuilder resp = ResponseEntity.ok();
        if (zip) {
            resp.header("Content-Type", "application/zip");
            resp.header("Content-Disposition", "attachment; filename=\"timetable-" + id + ".zip\"");
        } else {
            resp.header("Content-Type", "text/csv; charset=UTF-8");
            resp.header("Content-Disposition", "attachment; filename=\"timetable-" + id + ".csv\"");
        }
        return resp.body(csv);
    }

    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ALL") PdfExportService.View view,
            @RequestParam(required = false) Long entityId) {
        byte[] pdf = pdfExportService.exportPdf(id, view, entityId);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"timetable-" + id + ".pdf\"")
            .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/{id}/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ALL") ExcelExportService.View view,
            @RequestParam(required = false) Long entityId) {
        byte[] excel = excelExportService.exportExcel(id, view, entityId);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"timetable-" + id + ".xlsx\"")
            .contentType(org.springframework.http.MediaType
                .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excel);
    }

    @PostMapping("/feasibility-check")
    public ResponseEntity<FeasibilityCheckResult> checkFeasibility(
            @Valid @RequestBody ScheduleRequest req) {
        return ResponseEntity.ok(feasibilityCheckService.check(req));
    }
}
