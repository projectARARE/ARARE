package com.arare.features.subjectoffering;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subject-offerings")
@RequiredArgsConstructor
public class SubjectOfferingController {

    private final SubjectOfferingService service;

    @PostMapping
    public ResponseEntity<SubjectOfferingResponse> create(@Valid @RequestBody SubjectOfferingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping
    public ResponseEntity<List<SubjectOfferingResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectOfferingResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<SubjectOfferingResponse>> findByBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(service.findByBatch(batchId));
    }

    @GetMapping("/section/{sectionId}")
    public ResponseEntity<List<SubjectOfferingResponse>> findBySection(@PathVariable Long sectionId) {
        return ResponseEntity.ok(service.findBySection(sectionId));
    }

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<SubjectOfferingResponse>> findBySubject(@PathVariable Long subjectId) {
        return ResponseEntity.ok(service.findBySubject(subjectId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubjectOfferingResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody SubjectOfferingRequest req
    ) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}