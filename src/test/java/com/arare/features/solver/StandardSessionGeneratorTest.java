package com.arare.features.solver;

import com.arare.common.enums.RoomType;
import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.department.Department;
import com.arare.features.room.Room;
import com.arare.features.schedule.Schedule;
import com.arare.features.subject.Subject;
import com.arare.features.subjectoffering.SubjectOffering;
import com.arare.features.subjectoffering.SubjectOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandardSessionGeneratorTest {

    @Mock
    private SubjectOfferingRepository offeringRepo;

    private StandardSessionGenerator generator;

    private void stubNoOfferings() {
        org.mockito.Mockito.lenient()
            .when(offeringRepo.findByBatchIdIn(anyList())).thenReturn(List.of());
        org.mockito.Mockito.lenient()
            .when(offeringRepo.findBySectionIdIn(anyList())).thenReturn(List.of());
    }

    private Department dept() {
        Department d = new Department();
        d.setId(1L);
        d.setCode("CSE");
        d.setName("Computer Science");
        return d;
    }

    private Subject subject(Long id, String name, boolean lab, int weekly, int chunk) {
        Subject s = Subject.builder()
            .department(dept())
            .name(name)
            .code(name.toUpperCase())
            .isLab(lab)
            .requiresTeacher(true)
            .requiresRoom(true)
            .weeklyHours(weekly)
            .chunkHours(chunk)
            .build();
        s.setId(id);
        return s;
    }

    private Batch batch(Long id, List<Subject> curriculum) {
        Batch b = Batch.builder()
            .department(dept())
            .year(2)
            .section("A")
            .studentCount(60)
            .subjects(curriculum == null ? List.of() : curriculum)
            .build();
        b.setId(id);
        return b;
    }

    private ClassSection section(Long id, Batch batch, List<Subject> curriculum) {
        ClassSection cs = ClassSection.builder()
            .batch(batch)
            .label("A")
            .size(30)
            .subjects(curriculum)
            .build();
        cs.setId(id);
        return cs;
    }

    @Test
    void batchCurriculumScopesLectureSessions() {
        stubNoOfferings();
        generator = new StandardSessionGenerator(offeringRepo);
        Subject a = subject(10L, "DSA", false, 2, 1);
        Subject b = subject(11L, "OS", false, 2, 1);
        Batch selected = batch(1L, List.of(a));
        Batch inheritAll = batch(2L, List.of());

        List<ClassSession> sessions = generator.generate(
            Schedule.builder().build(),
            List.of(a, b),
            List.of(selected, inheritAll),
            List.of(),
            List.of()
        );

        // selected: DSA only (2). inheritAll: DSA + OS (4).
        assertEquals(6, sessions.size());
        long selectedDsa = sessions.stream()
            .filter(s -> s.getEffectiveBatch().getId().equals(1L))
            .filter(s -> s.getSubject().getId().equals(10L))
            .count();
        long selectedOs = sessions.stream()
            .filter(s -> s.getEffectiveBatch().getId().equals(1L))
            .filter(s -> s.getSubject().getId().equals(11L))
            .count();
        assertEquals(2, selectedDsa);
        assertEquals(0, selectedOs);
    }

    @Test
    void sectionCurriculumExcludesSplitLabSections() {
        stubNoOfferings();
        generator = new StandardSessionGenerator(offeringRepo);
        Subject lab = subject(20L, "DSA Lab", true, 4, 2);
        Batch batch = batch(1L, List.of(lab));
        ClassSection takesIt = section(1L, batch, List.of(lab));
        ClassSection skipsIt = section(2L, batch, List.of(subject(30L, "PSS", true, 2, 2)));

        Room labRoom = Room.builder()
            .type(RoomType.LAB)
            .capacity(30)
            .build();
        labRoom.setId(50L);

        List<ClassSession> sessions = generator.generate(
            Schedule.builder().build(),
            List.of(lab),
            List.of(batch),
            List.of(takesIt, skipsIt),
            List.of(labRoom)
        );

        // count = 4/2 = 2 occurrences, but only the section that offers the
        // subject generates sessions (skipsIt is excluded by its curriculum).
        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().allMatch(s -> s.getSection() != null
            && s.getSection().getId().equals(1L)
            && s.getSubject().getId().equals(20L)));
    }

    @Test
    void instituteWideSubjectGeneratesSessionsForForeignBatches() {
        stubNoOfferings();
        generator = new StandardSessionGenerator(offeringRepo);
        // Institute-wide subject has no owning department.
        Subject shared = Subject.builder()
            .department(null)
            .name("Environmental Studies")
            .code("ES101")
            .isLab(false)
            .requiresTeacher(true)
            .requiresRoom(true)
            .weeklyHours(2)
            .chunkHours(1)
            .build();
        shared.setId(40L);

        Batch cs = batch(1L, List.of());
        Batch ee = batch(2L, List.of());

        List<ClassSession> sessions = generator.generate(
            Schedule.builder().build(),
            List.of(shared),
            List.of(cs, ee),
            List.of(),
            List.of()
        );

        assertEquals(4, sessions.size());
        assertTrue(sessions.stream().allMatch(s -> s.getSubject().getId().equals(40L)));
    }

    @Test
    void offeringWeeklyHoursOverrideDrivesSessionCount() {
        Subject a = subject(10L, "DSA", false, 2, 1);
        Batch selected = batch(1L, List.of());

        SubjectOffering override = SubjectOffering.builder()
            .subject(a)
            .batch(selected)
            .weeklyHours(4)
            .build();

        generator = new StandardSessionGenerator(offeringRepo);
        org.mockito.Mockito.lenient()
            .when(offeringRepo.findByBatchIdIn(anyList())).thenReturn(List.of(override));
        org.mockito.Mockito.lenient()
            .when(offeringRepo.findBySectionIdIn(anyList())).thenReturn(List.of());

        List<ClassSession> sessions = generator.generate(
            Schedule.builder().build(),
            List.of(a),
            List.of(selected),
            List.of(),
            List.of()
        );

        // Override bumps catalogue 2h/week to 4h/week -> 4 sessions, not 2.
        assertEquals(4, sessions.size());
        assertTrue(sessions.stream().allMatch(s -> s.getSubject().getId().equals(10L)));
    }
}