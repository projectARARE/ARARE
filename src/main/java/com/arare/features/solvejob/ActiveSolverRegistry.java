package com.arare.features.solvejob;

import ai.timefold.solver.core.api.solver.Solver;
import com.arare.features.solver.TimetableSolution;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Registry of live Timefold solvers keyed by problem id, so a cancel request
 * can reach the running solver even though it executes on a worker thread.
 */
@Component
public class ActiveSolverRegistry {

    private final Map<UUID, Solver<TimetableSolution>> active = new ConcurrentHashMap<>();

    public void register(UUID problemId, Solver<TimetableSolution> solver) {
        active.put(problemId, solver);
    }

    public Solver<TimetableSolution> get(UUID problemId) {
        return active.get(problemId);
    }

    public void unregister(UUID problemId) {
        active.remove(problemId);
    }
}
