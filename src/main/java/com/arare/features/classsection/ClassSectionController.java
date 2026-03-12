package com.arare.features.classsection;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/class-sections")
@RequiredArgsConstructor
public class ClassSectionController {

    private final ClassSectionService service;

    @PostMapping
    public ResponseEntity<ClassSectionResponse> create(@Valid @RequestBody ClassSectionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClassSectionResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody ClassSectionRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @GetMapping
    public ResponseEntity<List<ClassSectionResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassSectionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<ClassSectionResponse>> findByBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(service.findByBatch(batchId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
