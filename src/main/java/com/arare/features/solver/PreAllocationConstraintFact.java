package com.arare.features.solver;

// A teacher (and optionally room) pinned by a pre-allocation that does NOT
// fix the timeslot. {@code isLocked} (=@PlanningPin) pins every planning
// variable, so a teacher-only pre-allocation cannot use it; instead this fact
// records the pinned values and the solver keeps them via a HARD constraint
// while still being free to choose a timeslot.
public record PreAllocationConstraintFact(
    Long sessionId,
    Long teacherId,
    Long roomId
) {}