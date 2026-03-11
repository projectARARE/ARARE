package com.arare.features.schedule;

import com.arare.features.classsession.ClassSessionResponse;
import com.arare.features.solver.ScoreExplanationResponse;
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

    private final ScheduleService service;

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
}
