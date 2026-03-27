package com.arare.features.universityconfig;

import com.arare.common.enums.TimeslotType;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniversityConfigServiceImpl implements UniversityConfigService {

    private final UniversityConfigRepository repo;
    private final TimeslotRepository timeslotRepo;

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

    @Override
    public UniversityConfigDiagnosticsResponse getDiagnostics() {
        UniversityConfig cfg = repo.findByActiveTrue()
            .orElseThrow(() -> new ResourceNotFoundException("No active UniversityConfig found"));

        var classSlots = timeslotRepo.findByType(TimeslotType.CLASS);
        var classSlotsPerDay = new LinkedHashMap<String, Integer>();
        classSlots.forEach(ts -> classSlotsPerDay.merge(ts.getDay().name(), 1, Integer::sum));

        List<String> issues = new ArrayList<>();
        List<String> workingDays = cfg.getWorkingDays().stream().map(Enum::name).toList();

        if (!workingDays.isEmpty() && workingDays.size() != cfg.getDaysPerWeek()) {
            issues.add("workingDays count does not match daysPerWeek.");
        }

        if (!workingDays.isEmpty()) {
            for (String day : workingDays) {
                if (!classSlotsPerDay.containsKey(day)) {
                    issues.add("Missing CLASS timeslots for configured working day: " + day);
                }
            }
        } else if (classSlotsPerDay.size() < cfg.getDaysPerWeek()) {
            issues.add("CLASS timeslots cover fewer days than configured daysPerWeek.");
        }

        classSlotsPerDay.forEach((day, count) -> {
            if (count < cfg.getTimeslotsPerDay()) {
                issues.add(day + " has only " + count + " CLASS slots, below timeslotsPerDay="
                    + cfg.getTimeslotsPerDay());
            }
        });

        for (Integer idx : cfg.getBreakSlotIndices()) {
            if (idx == null || idx < 0 || idx >= cfg.getTimeslotsPerDay()) {
                issues.add("Invalid breakSlot index " + idx + " (must be in [0, timeslotsPerDay-1]).");
            }
        }

        boolean valid = issues.isEmpty();
        return new UniversityConfigDiagnosticsResponse(
            valid,
            valid ? "University configuration and timeslot topology are aligned."
                  : "Configuration/topology mismatches detected.",
            cfg.getDaysPerWeek(),
            cfg.getTimeslotsPerDay(),
            cfg.getMaxClassesPerDay(),
            workingDays,
            classSlotsPerDay,
            issues
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
