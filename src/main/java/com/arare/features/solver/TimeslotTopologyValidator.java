package com.arare.features.solver;

import com.arare.features.timeslot.Timeslot;
import com.arare.features.universityconfig.UniversityConfig;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TimeslotTopologyValidator {

    public void validate(List<Timeslot> classTimeslots, List<UniversityConfig> configs) {
        if (configs.isEmpty()) {
            return;
        }

        UniversityConfig cfg = configs.get(0);

        Set<String> daysPresent = classTimeslots.stream()
            .map(t -> t.getDay().name())
            .collect(Collectors.toSet());

        List<String> workingDays = cfg.getWorkingDays().stream().map(Enum::name).toList();
        if (!workingDays.isEmpty() && workingDays.size() != cfg.getDaysPerWeek()) {
            throw new IllegalStateException(
                "UniversityConfig invalid: workingDays count must match daysPerWeek.");
        }

        if (!workingDays.isEmpty()) {
            for (String day : workingDays) {
                if (!daysPresent.contains(day)) {
                    throw new IllegalStateException(
                        "Timeslot topology mismatch: missing CLASS timeslots for configured working day " + day + ".");
                }
            }
        } else if (daysPresent.size() < cfg.getDaysPerWeek()) {
            throw new IllegalStateException(
                "Timeslot topology mismatch: daysPerWeek=" + cfg.getDaysPerWeek()
                    + " but only " + daysPresent.size() + " day(s) have CLASS timeslots.");
        }

        for (Integer breakIdx : cfg.getBreakSlotIndices()) {
            if (breakIdx == null || breakIdx < 0) {
                throw new IllegalStateException(
                    "UniversityConfig invalid: breakSlotIndices cannot be negative.");
            }
        }
    }
}
