package com.arare.features.solver;

import com.arare.features.schedule.Schedule;
import java.util.List;

public record ProblemBuildRequest(
    Schedule schedule,
    List<Long> impactedSessionIds,
    Long departmentId,
    List<Long> batchIds,
    List<Long> teacherIds,
    List<Long> roomIds
) {}
