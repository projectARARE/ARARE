package com.arare.features.solver;

import com.arare.common.enums.SchoolDay;

import java.time.LocalTime;
import java.util.Objects;

// A (room, day, slot-range) combination already booked in another ACTIVE
// (live) schedule. Fed into the solver as a problem fact so a session is never
// assigned to a slot where that room is already in use elsewhere -- the
// physical equivalent of TeacherBusyInterval, preventing one room being
// double-booked across independently generated timetables.
public record RoomBusyInterval(
    Long roomId,
    SchoolDay day,
    Integer startSlot,
    Integer endSlotExclusive,
    LocalTime startTime,
    LocalTime endTime
) {
    public boolean sameRoom(Long otherRoomId) {
        return Objects.equals(roomId, otherRoomId);
    }

    public boolean sameDay(SchoolDay otherDay) {
        return Objects.equals(day, otherDay);
    }
}
