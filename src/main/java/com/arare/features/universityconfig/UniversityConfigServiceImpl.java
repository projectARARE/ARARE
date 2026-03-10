package com.arare.features.universityconfig;

import com.arare.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniversityConfigServiceImpl implements UniversityConfigService {

    private final UniversityConfigRepository repo;

    @Override
    @Transactional
    public UniversityConfigResponse save(UniversityConfigRequest req) {
        // Deactivate any existing active config before saving the new one
        repo.findByActiveTrue().ifPresent(existing -> {
            existing.setActive(false);
            repo.save(existing);
        });

        UniversityConfig config = UniversityConfig.builder()
            .active(true)
            .daysPerWeek(req.daysPerWeek())
            .timeslotsPerDay(req.timeslotsPerDay())
            .maxClassesPerDay(req.maxClassesPerDay())
            .breakSlotIndices(req.breakSlotIndices() == null ? List.of() : req.breakSlotIndices())
            .workingDays(req.workingDays() == null ? List.of() : req.workingDays())
            .build();
        return toResponse(repo.save(config));
    }

    @Override
    public UniversityConfigResponse getActive() {
        return toResponse(
            repo.findByActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No active UniversityConfig found"))
        );
    }

    private UniversityConfigResponse toResponse(UniversityConfig c) {
        return new UniversityConfigResponse(
            c.getId(), c.isActive(),
            c.getDaysPerWeek(), c.getTimeslotsPerDay(), c.getMaxClassesPerDay(),
            c.getBreakSlotIndices(), c.getWorkingDays()
        );
    }
}
