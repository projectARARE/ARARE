package com.arare.features.teacherassignment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher-assignments")
@RequiredArgsConstructor
public class TeacherAssignmentController {

    private final TeacherAssignmentService service;

    @PostMapping
    public ResponseEntity<TeacherAssignmentResponse> create(@Valid @RequestBody TeacherAssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping
    public ResponseEntity<List<TeacherAssignmentResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherAssignmentResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<List<TeacherAssignmentResponse>> findByTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(service.findByTeacher(teacherId));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<TeacherAssignmentResponse>> findByBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(service.findByBatch(batchId));
    }

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<TeacherAssignmentResponse>> findBySubject(@PathVariable Long subjectId) {
        return ResponseEntity.ok(service.findBySubject(subjectId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeacherAssignmentResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody TeacherAssignmentRequest req
    ) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}