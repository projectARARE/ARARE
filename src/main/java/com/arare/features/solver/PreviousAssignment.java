package com.arare.features.solver;

import com.arare.features.classsession.ClassSession;

// A session's assignment in a PARENT schedule, used to minimise disruption on
// regenerate: the solver is nudged (soft) to keep each session where it
// already is instead of reshuffling the whole timetable for a re-solve.
// <p>{@code key} groups sessions that represent the same subject-occurrence
// (subject, effective batch, section, duration) and {@link #matches} tells the
// solver whether that occurrence is still in the same teacher/room/timeslot
// as it was in the parent.</p>
public record PreviousAssignment(
    String key,
    Long teacherId,
    Long roomId,
    Long timeslotId
) {
    public boolean matches(ClassSession s) {
        return s.getTeacher() != null && s.getTeacher().getId().equals(teacherId)
            && s.getRoom() != null && s.getRoom().getId().equals(roomId)
            && s.getTimeslot() != null && s.getTimeslot().getId().equals(timeslotId);
    }

    // Mirrors TimetableConstraintProvider.movementKey so both sides agree on
    // what "the same subject-occurrence" means.
    public static String keyFor(ClassSession s) {
        return s.getSubject().getId() + ":"
            + (s.getEffectiveBatch() != null ? s.getEffectiveBatch().getId() : -1L) + ":"
            + (s.getSection() != null ? s.getSection().getId() : -1L) + ":"
            + s.getDuration();
    }
}
