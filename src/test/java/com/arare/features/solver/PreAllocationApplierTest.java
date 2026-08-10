package com.arare.features.solver;

import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.preallocation.PreAllocation;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreAllocationApplierTest {

    private final PreAllocationApplier applier = new PreAllocationApplier();

    @Test
    void appliesToSectionBasedLabSessionViaParentBatch() {
        Batch batch = Batch.builder().year(2).section("A").studentCount(60).build();
        batch.setId(1L);

        ClassSection section = ClassSection.builder().label("A1").size(30).build();
        section.setId(5L);
        section.setBatch(batch);

        Subject subject = Subject.builder().name("Physics Lab").isLab(true).build();
        subject.setId(2L);

        Teacher teacher = Teacher.builder().name("Dr. X").build();
        teacher.setId(3L);
        Room room = Room.builder().roomNumber("L-101").build();
        room.setId(4L);
        Timeslot timeslot = new Timeslot();
        timeslot.setId(6L);

        ClassSession session = ClassSession.builder()
            .id(10L)
            .subject(subject)
            .section(section)
            .duration(1)
            .isLocked(false)
            .build();

        PreAllocation pa = PreAllocation.builder()
            .schedule(null)
            .batch(batch)
            .subject(subject)
            .teacher(teacher)
            .room(room)
            .timeslot(timeslot)
            .locked(true)
            .build();
        pa.setId(20L);

        applier.apply(List.of(session), List.of(pa));

        assertTrue(session.isLocked());
        assertSame(teacher, session.getTeacher());
        assertSame(room, session.getRoom());
        assertSame(timeslot, session.getTimeslot());
    }

    @Test
    void doesNotMatchDifferentSubject() {
        Batch batch = Batch.builder().year(2026).section("A").studentCount(60).build();
        batch.setId(1L);

        Subject sessionSubject = Subject.builder().name("Maths").build();
        sessionSubject.setId(2L);
        Subject paSubject = Subject.builder().name("Physics").build();
        paSubject.setId(3L);

        ClassSession session = ClassSession.builder()
            .id(10L)
            .subject(sessionSubject)
            .batch(batch)
            .duration(1)
            .isLocked(false)
            .build();

        PreAllocation pa = PreAllocation.builder()
            .batch(batch)
            .subject(paSubject)
            .timeslot(new Timeslot())
            .locked(true)
            .build();

        applier.apply(List.of(session), List.of(pa));

        assertFalse(session.isLocked());
        assertNull(session.getTeacher());
        assertNull(session.getRoom());
    }
}