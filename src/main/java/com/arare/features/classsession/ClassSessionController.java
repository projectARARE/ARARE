package com.arare.features.classsession;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class ClassSessionController {

    private final ClassSessionService service;

    @GetMapping("/schedule/{scheduleId}")
    public ResponseEntity<List<ClassSessionResponse>> findBySchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(service.findBySchedule(scheduleId));
    }

    @GetMapping("/schedule/{scheduleId}/batch/{batchId}")
    public ResponseEntity<List<ClassSessionResponse>> findByScheduleAndBatch(
        @PathVariable Long scheduleId, @PathVariable Long batchId
    ) {
        return ResponseEntity.ok(service.findByScheduleAndBatch(scheduleId, batchId));
    }

    @GetMapping("/schedule/{scheduleId}/teacher/{teacherId}")
    public ResponseEntity<List<ClassSessionResponse>> findByScheduleAndTeacher(
        @PathVariable Long scheduleId, @PathVariable Long teacherId
    ) {
        return ResponseEntity.ok(service.findByScheduleAndTeacher(scheduleId, teacherId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ClassSessionResponse> updateAssignment(
        @PathVariable Long id,
        @Valid @RequestBody SessionAssignmentRequest req
    ) {
        return ResponseEntity.ok(service.updateAssignment(id, req));
    }

    @PostMapping
    public ResponseEntity<ClassSessionResponse> create(
        @Valid @RequestBody SessionCreateRequest req
    ) {
        return ResponseEntity.ok(service.create(req));
    }
}
