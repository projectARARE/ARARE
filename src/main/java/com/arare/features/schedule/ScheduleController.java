package com.arare.features.schedule;

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
