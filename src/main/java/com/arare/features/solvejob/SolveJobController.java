package com.arare.features.solvejob;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/solve-jobs")
@RequiredArgsConstructor
public class SolveJobController {

    private final SolveJobService solveJobService;

    @GetMapping
    public ResponseEntity<List<SolveJobResponse>> list(
        @RequestParam(required = false) SolveJobStatus status
    ) {
        return ResponseEntity.ok(solveJobService.list(status));
    }

    @GetMapping("/schedule/{scheduleId}")
    public ResponseEntity<List<SolveJobResponse>> listForSchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(solveJobService.listForSchedule(scheduleId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolveJobResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(solveJobService.get(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SolveJobResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(solveJobService.cancel(id));
    }
}
