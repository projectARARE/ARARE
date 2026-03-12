package com.arare.features.academicterm;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/academic-terms")
@RequiredArgsConstructor
public class AcademicTermController {

    private final AcademicTermService service;

    @GetMapping
    public List<AcademicTermResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AcademicTermResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicTermResponse create(@Valid @RequestBody AcademicTermRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AcademicTermResponse update(@PathVariable Long id,
                                       @Valid @RequestBody AcademicTermRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
