package com.arare.features.solver;

import com.arare.common.enums.SchoolDay;

import java.time.LocalTime;
import java.util.Objects;

// A (teacher, day, slot-range) combination already booked in another ACTIVE
// (live) schedule. Fed into the solver as a problem fact so sessions are never
// assigned to a slot where that teacher is already teaching elsewhere — the
// equivalent of a resource-availability fact, not a post-solve rejection.
public record TeacherBusyInterval(
    Long teacherId,
    SchoolDay day,
    Integer startSlot,
    Integer endSlotExclusive,
    LocalTime startTime,
    LocalTime endTime
) {
    public boolean sameTeacher(Long otherTeacherId) {
        return Objects.equals(teacherId, otherTeacherId);
    }

    public boolean sameDay(SchoolDay otherDay) {
        return Objects.equals(day, otherDay);
    }
}