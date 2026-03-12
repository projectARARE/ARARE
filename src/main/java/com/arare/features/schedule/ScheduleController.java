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
    private final FeasibilityCheckService  feasibilityCheckService;

    /** Generates a new timetable. Triggers the solver. */
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

    /**
     * Triggers partial re-optimization.
     * Body: list of impacted ClassSession IDs.
     */
    @PostMapping("/{id}/partial-resolve")
    public ResponseEntity<ScheduleResponse> partialResolve(
        @PathVariable Long id,
        @RequestBody List<Long> impactedSessionIds
    ) {
        return ResponseEntity.ok(service.partialResolve(id, impactedSessionIds));
    }

    /**
     * Returns a constraint-by-constraint score explanation for the schedule.
     * Useful for the UI "Why is my schedule scoring X?" panel.
     */
    @GetMapping("/{id}/score-explanation")
    public ResponseEntity<ScoreExplanationResponse> scoreExplanation(@PathVariable Long id) {
        return ResponseEntity.ok(service.explainScore(id));
    }

    /**
     * Returns the stored plain-text score explanation that was generated
     * when the schedule was last solved.
     */
    @GetMapping("/{id}/explanation")
    public ResponseEntity<String> getExplanation(@PathVariable Long id) {
        return ResponseEntity.ok(service.getExplanation(id));
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

    // ─── Disruption / Impact Analyzer ──────────────────────────────────────────

    /**
     * Preview which sessions would be impacted by a disruption.
     * Does NOT re-solve. Use this to show the user what will change before committing.
     */
    @PostMapping("/{id}/disruption/preview")
    public ResponseEntity<DisruptionResponse> previewDisruption(
            @PathVariable Long id,
            @Valid @RequestBody DisruptionRequest request) {
        return ResponseEntity.ok(disruptionService.previewImpact(id, request));
    }

    /**
     * Apply a disruption: automatically identifies impacted sessions via the
     * Dependency Graph and triggers partial re-optimization on only those sessions.
     */
    @PostMapping("/{id}/disruption/apply")
    public ResponseEntity<ScheduleResponse> applyDisruption(
            @PathVariable Long id,
            @Valid @RequestBody DisruptionRequest request) {
        return ResponseEntity.ok(disruptionService.applyDisruption(id, request));
    }

    // ─── Export ────────────────────────────────────────────────────────────────

    /**
     * Downloads the solved timetable as a CSV file.
     * Only assigned sessions are included (sessions with a timeslot).
     * A UTF-8 BOM is included so Excel opens the file correctly.
     */
    @GetMapping("/{id}/export/csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id) {
        byte[] csv = exportService.exportCsv(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(
            ContentDisposition.attachment().filename("timetable-" + id + ".csv").build());
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    // ─── Feasibility Check (Constraint Propagation) ────────────────────────────

    /**
     * Pre-solve feasibility check — runs before the solver to surface hard errors
     * and warnings without wasting solver time on infeasible configurations.
     *
     * <p>Accepts the same filtering fields as {@code /generate} (scope, departmentId,
     * batchIds, teacherIds, roomIds) but does NOT require a schedule name or trigger
     * any database writes.</p>
     */
    @PostMapping("/feasibility-check")
    public ResponseEntity<FeasibilityCheckResult> checkFeasibility(
            @RequestBody ScheduleRequest req) {
        return ResponseEntity.ok(feasibilityCheckService.check(req));
    }
}
