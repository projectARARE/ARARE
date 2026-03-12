package com.arare.features.impact;

import com.arare.features.schedule.ScheduleResponse;

public interface DisruptionService {

    /**
     * Preview which sessions would be impacted by a disruption.
     * Does NOT re-solve — purely analytical.
     *
     * @param scheduleId The active schedule to analyze.
     * @param request    Disruption description.
     * @return List of affected sessions and their IDs.
     */
    DisruptionResponse previewImpact(Long scheduleId, DisruptionRequest request);

    /**
     * Apply a disruption: automatically finds impacted sessions and triggers
     * partial re-optimization on only those sessions.
     *
     * @param scheduleId The schedule to update.
     * @param request    Disruption description.
     * @return Updated schedule after re-solve.
     */
    ScheduleResponse applyDisruption(Long scheduleId, DisruptionRequest request);
}
