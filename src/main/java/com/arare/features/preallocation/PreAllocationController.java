package com.arare.features.preallocation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pre-allocations")
@RequiredArgsConstructor
public class PreAllocationController {

    private final PreAllocationService service;

    @PostMapping
    public ResponseEntity<PreAllocationResponse> create(@Valid @RequestBody PreAllocationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreAllocationResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/schedule/{scheduleId}")
    public ResponseEntity<List<PreAllocationResponse>> findBySchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(service.findBySchedule(scheduleId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
