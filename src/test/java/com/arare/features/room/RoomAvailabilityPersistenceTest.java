package com.arare.features.room;

import com.arare.common.enums.RoomType;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

// The ManyToMany join table (room_availability) is only flushed when the
// collection Hibernate is tracking is mutated IN PLACE. Replacing the list
// reference inside @PrePersist/@PreUpdate silently drops the join rows on
// update (create worked because the entity was transient). These tests pin
// that behaviour so the churn repair path (marking a room unavailable) keeps
// working.
class RoomAvailabilityPersistenceTest {

    @Test
    void normalizeDedupesInPlaceAndKeepsSameCollectionInstance() {
        Timeslot ts1 = new Timeslot();
        ts1.setId(1L);
        Timeslot ts2 = new Timeslot();
        ts2.setId(2L);

        List<Timeslot> original = new ArrayList<>(List.of(ts1, ts2, ts1));
        Room room = Room.builder()
            .type(RoomType.LECTURE)
            .capacity(40)
            .availableTimeslots(original)
            .build();

        // Force the @PrePersist/@PreUpdate hook (private, invoked via reflection).
        try {
            var method = Room.class.getDeclaredMethod("normalizeAndValidate");
            method.setAccessible(true);
            method.invoke(room);
        } catch (Exception e) {
            throw new AssertionError("could not invoke normalizeAndValidate", e);
        }

        // Dedupe happened (3 -> 2), but the list instance is unchanged so
        // Hibernate's dirty tracking still owns it.
        assertSame(original, room.getAvailableTimeslots());
    }
}