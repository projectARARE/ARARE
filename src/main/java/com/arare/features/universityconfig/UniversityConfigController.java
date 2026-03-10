package com.arare.features.universityconfig;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/university-config")
@RequiredArgsConstructor
public class UniversityConfigController {

    private final UniversityConfigService service;

    /** Creates or replaces the active university configuration. */
    @PostMapping
    public ResponseEntity<UniversityConfigResponse> save(@Valid @RequestBody UniversityConfigRequest req) {
        return ResponseEntity.ok(service.save(req));
    }

    @GetMapping
    public ResponseEntity<UniversityConfigResponse> getActive() {
        return ResponseEntity.ok(service.getActive());
    }
}
