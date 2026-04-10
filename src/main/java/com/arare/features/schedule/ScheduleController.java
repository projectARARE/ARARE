package com.arare.features.schedule;

import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.impact.DisruptionRequest;
import com.arare.features.impact.DisruptionResponse;
import com.arare.features.impact.DisruptionService;
import com.arare.features.solver.ScoreExplanationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final TimetableCalendarExportService calendarExportService;
    private final FeasibilityCheckService  feasibilityCheckService;

    @PostMapping("/generate")
    public ResponseEntity<ScheduleResponse> generate(@Valid @RequestBody ScheduleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping("/{id}/partial-resolve")
    public ResponseEntity<ScheduleResponse> partialResolve(
        @PathVariable Long id,
        @RequestBody List<Long> impactedSessionIds
    ) {
        return ResponseEntity.ok(service.partialResolve(id, impactedSessionIds));
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
    public ResponseEntity<ScheduleResponse> applyDisruption(
            @PathVariable Long id,
            @Valid @RequestBody DisruptionRequest request) {
        return ResponseEntity.ok(disruptionService.applyDisruption(id, request));
    }
    @GetMapping("/{id}/export/csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id) {
        byte[] csv = exportService.exportCsv(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(
            ContentDisposition.attachment().filename("timetable-" + id + ".csv").build());
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @GetMapping(value = "/ical/teacher/{teacherId}", produces = "text/calendar; charset=UTF-8")
    public ResponseEntity<byte[]> exportTeacherIcal(
            @PathVariable Long teacherId,
            @RequestParam(required = false) Long scheduleId
    ) {
        byte[] ics = calendarExportService.exportTeacherCalendar(teacherId, scheduleId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"));
        headers.setContentDisposition(
            ContentDisposition.inline().filename("teacher-" + teacherId + ".ics").build());
        return new ResponseEntity<>(ics, headers, HttpStatus.OK);
    }

    @GetMapping(value = "/ical/batch/{batchId}", produces = "text/calendar; charset=UTF-8")
    public ResponseEntity<byte[]> exportBatchIcal(
            @PathVariable Long batchId,
            @RequestParam(required = false) Long scheduleId
    ) {
        byte[] ics = calendarExportService.exportBatchCalendar(batchId, scheduleId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"));
        headers.setContentDisposition(
            ContentDisposition.inline().filename("batch-" + batchId + ".ics").build());
        return new ResponseEntity<>(ics, headers, HttpStatus.OK);
    }

    @PostMapping("/feasibility-check")
    public ResponseEntity<FeasibilityCheckResult> checkFeasibility(
            @RequestBody ScheduleRequest req) {
        return ResponseEntity.ok(feasibilityCheckService.check(req));
    }
}
