package com.arare.features.solver;

import com.arare.features.classsession.ClassSession;
import com.arare.features.preallocation.PreAllocation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PreAllocationApplier {

    /**
     * Applies pre-allocations to the freshly generated sessions.
     *
     * <p>A pre-allocation WITH a timeslot fully pins teacher + room + timeslot
     * ({@code isLocked=true}). A pre-allocation WITHOUT a timeslot pins only the
     * teacher (and optionally room) via a {@link PreAllocationConstraintFact}:
     * the solver keeps those values (HARD constraint) while remaining free to
     * choose a compatible slot, because {@code @PlanningPin} would freeze the
     * timeslot too and leave the session unassigned.</p>
     *
     * <p>Sessions in {@code impactedSessionIds} are exempt from ALL pins: a
     * disruption targeting a pre-allocated session (e.g. its teacher is
     * unavailable) must be free to move it, or the repair is unsolvable by
     * construction.</p>
     *
     * @return the partial-pin facts for pre-allocations that left the slot open
     */
    public List<PreAllocationConstraintFact> apply(
            List<ClassSession> sessions, List<PreAllocation> lockedPreAllocations) {
        return apply(sessions, lockedPreAllocations, List.of());
    }

    public List<PreAllocationConstraintFact> apply(
            List<ClassSession> sessions,
            List<PreAllocation> lockedPreAllocations,
            List<Long> impactedSessionIds) {
        List<PreAllocationConstraintFact> facts = new ArrayList<>();
        for (PreAllocation pa : lockedPreAllocations) {
            sessions.stream()
                .filter(s -> !s.isLocked()
                    && s.getSubject() != null
                    && s.getSubject().getId().equals(pa.getSubject().getId())
                    && s.getEffectiveBatch() != null
                    && s.getEffectiveBatch().getId().equals(pa.getBatch().getId()))
                .findFirst()
                .ifPresent(s -> {
                    if (impactedSessionIds.contains(s.getId())) {
                        return;
                    }
                    if (pa.getTimeslot() != null) {
                        // Full pin: teacher + room + timeslot fixed.
                        s.setTeacher(pa.getTeacher());
                        s.setRoom(pa.getRoom());
                        s.setTimeslot(pa.getTimeslot());
                        s.setLocked(true);
                    } else if (pa.getTeacher() != null) {
                        // Partial pin: keep teacher (and optionally room), the
                        // solver remains free to choose a compatible slot.
                        s.setTeacher(pa.getTeacher());
                        s.setRoom(pa.getRoom());
                        facts.add(new PreAllocationConstraintFact(
                            s.getId(),
                            pa.getTeacher().getId(),
                            pa.getRoom() != null ? pa.getRoom().getId() : null));
                    }
                });
        }
        return facts;
    }
}
