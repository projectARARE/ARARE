package com.arare.features.solver;

import com.arare.common.enums.RoomType;
import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.schedule.Schedule;
import com.arare.features.subject.Subject;
import com.arare.features.subjectoffering.SubjectOffering;
import com.arare.features.subjectoffering.SubjectOfferingRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StandardSessionGenerator implements SessionGenerator {

    private final SubjectOfferingRepository offeringRepo;

    @Override
    public List<ClassSession> generate(
        Schedule schedule,
        List<Subject> subjects,
        List<Batch> batches,
        List<ClassSection> sections,
        List<Room> rooms
    ) {
        List<ClassSession> generated = new ArrayList<>();

        // Preload offerings for the batches/sections in scope so curriculum
        // scoping and per-offering weeklyHours overrides are honoured without
        // a query per (batch, subject) pair.
        List<Long> batchIds = batches.stream().map(Batch::getId).toList();
        List<Long> sectionIds = sections.stream().map(ClassSection::getId).toList();
        Map<Long, List<SubjectOffering>> batchOfferings = indexOfferings(
            batchIds.isEmpty() ? List.of() : offeringRepo.findByBatchIdIn(batchIds));
        Map<Long, List<SubjectOffering>> sectionOfferings = indexOfferings(
            sectionIds.isEmpty() ? List.of() : offeringRepo.findBySectionIdIn(sectionIds));

        List<Subject> subjectsByChunkFirst = subjects.stream()
            .sorted(Comparator.comparingInt(Subject::getChunkHours).reversed())
            .toList();

        for (Batch batch : batches) {
            for (Subject subject : subjectsByChunkFirst) {
                // Institute-wide subjects (no owning department) are offered to
                // every batch; otherwise the subject must belong to the batch's
                // department.
                if (subject.getDepartment() != null
                    && !subject.getDepartment().getId().equals(batch.getDepartment().getId())) {
                    continue;
                }
                // Curriculum scoping: an explicit offering list wins; otherwise
                // a batch that declares its own legacy curriculum only generates
                // sessions for the subjects it actually offers. Empty curriculum
                // = inherit everything the department offers.
                if (!batchTakesSubject(batch, subject, batchOfferings)) {
                    continue;
                }

                validateSubjectChunking(subject);
                int count = effectiveWeeklyHours(batch, subject, batchOfferings)
                    / subject.getChunkHours();

                if (subject.isLab()) {
                    generateLabSessions(generated, schedule, subject, batch, sections, rooms,
                        count, batchOfferings, sectionOfferings);
                } else {
                    generateLectureSessions(generated, schedule, subject, batch, count);
                }
            }
        }

        return generated;
    }

    private Map<Long, List<SubjectOffering>> indexOfferings(List<SubjectOffering> offerings) {
        return offerings.stream().collect(Collectors.groupingBy(o -> {
            if (o.getBatch() != null) {
                return o.getBatch().getId();
            }
            return o.getSection().getId();
        }));
    }

    private void validateSubjectChunking(Subject subject) {
        if (subject.getChunkHours() <= 0) {
            throw new IllegalStateException(
                "Invalid chunkHours for subject " + subject.getName() + ": must be > 0");
        }
        if (subject.getWeeklyHours() % subject.getChunkHours() != 0) {
            throw new IllegalStateException(
                "Subject " + subject.getName() + " has weeklyHours=" + subject.getWeeklyHours()
                    + " not divisible by chunkHours=" + subject.getChunkHours()
                    + ". This would drop slot units during session generation.");
        }
    }

    private void generateLabSessions(
        List<ClassSession> generated,
        Schedule schedule,
        Subject subject,
        Batch batch,
        List<ClassSection> sections,
        List<Room> rooms,
        int count,
        Map<Long, List<SubjectOffering>> batchOfferings,
        Map<Long, List<SubjectOffering>> sectionOfferings
    ) {
        boolean canRunWholeBatch = rooms.stream().anyMatch(room ->
            room.getType() == RoomType.LAB
                && room.getCapacity() >= batch.getStudentCount()
                && roomMatchesLabSubtype(subject, room)
        );

        List<ClassSection> batchSections = sections.stream()
            .filter(s -> s.getBatch().getId().equals(batch.getId()))
            .filter(s -> sectionTakesSubject(s, subject, sectionOfferings))
            .toList();

        boolean canRunBySections = !batchSections.isEmpty() && batchSections.stream().allMatch(section ->
            rooms.stream().anyMatch(room -> room.getType() == RoomType.LAB
                && room.getCapacity() >= section.getSize()
                && roomMatchesLabSubtype(subject, room))
        );

        if (canRunWholeBatch) {
            for (int i = 0; i < count; i++) {
                generated.add(createSession(schedule, subject, batch, null));
            }
            return;
        }

        if (!canRunBySections) {
            for (int i = 0; i < count; i++) {
                generated.add(createSession(schedule, subject, batch, null));
            }
            return;
        }

        for (int i = 0; i < count; i++) {
            for (ClassSection section : batchSections) {
                generated.add(createSession(schedule, subject, null, section));
            }
        }
    }

    private void generateLectureSessions(
        List<ClassSession> generated,
        Schedule schedule,
        Subject subject,
        Batch batch,
        int count
    ) {
        for (int i = 0; i < count; i++) {
            generated.add(createSession(schedule, subject, batch, null));
        }
    }

    private ClassSession createSession(Schedule schedule, Subject subject, Batch batch, ClassSection section) {
        return ClassSession.builder()
            .subject(subject)
            .batch(batch)
            .section(section)
            .schedule(schedule)
            .duration(subject.getChunkHours())
            .isLocked(false)
            .build();
    }

    // Curriculum fallback chain: explicit SubjectOffering list -> section
    // curriculum -> batch curriculum -> department-offered (all subjects).
    // Empty curriculum means "inherit".
    private boolean batchTakesSubject(Batch batch, Subject subject,
                                      Map<Long, List<SubjectOffering>> batchOfferings) {
        List<SubjectOffering> offerings = batchOfferings.get(batch.getId());
        if (offerings != null && !offerings.isEmpty()) {
            return offerings.stream().anyMatch(o -> o.getSubject().getId().equals(subject.getId()));
        }
        List<Subject> curriculum = batch.getSubjects();
        return curriculum == null || curriculum.isEmpty()
            || curriculum.stream().anyMatch(s -> s.getId().equals(subject.getId()));
    }

    private boolean sectionTakesSubject(ClassSection section, Subject subject,
                                        Map<Long, List<SubjectOffering>> sectionOfferings) {
        List<SubjectOffering> offerings = sectionOfferings.get(section.getId());
        if (offerings != null && !offerings.isEmpty()) {
            return offerings.stream().anyMatch(o -> o.getSubject().getId().equals(subject.getId()));
        }
        List<Subject> curriculum = section.getSubjects();
        return curriculum == null || curriculum.isEmpty()
            || curriculum.stream().anyMatch(s -> s.getId().equals(subject.getId()));
    }

    // Per-offering weeklyHours override (null = catalogue weeklyHours). Only
    // consulted when the batch has explicit offerings.
    private int effectiveWeeklyHours(Batch batch, Subject subject,
                                     Map<Long, List<SubjectOffering>> batchOfferings) {
        List<SubjectOffering> offerings = batchOfferings.get(batch.getId());
        if (offerings != null && !offerings.isEmpty()) {
            for (SubjectOffering o : offerings) {
                if (o.getSubject().getId().equals(subject.getId()) && o.getWeeklyHours() != null) {
                    if (o.getWeeklyHours() % subject.getChunkHours() != 0) {
                        throw new IllegalStateException(
                            "SubjectOffering for " + subject.getName() + " in batch "
                                + batch.getDepartment().getCode() + "-" + batch.getYear()
                                + batch.getSection() + " has weeklyHours=" + o.getWeeklyHours()
                                + " not divisible by chunkHours=" + subject.getChunkHours() + ".");
                    }
                    return o.getWeeklyHours();
                }
            }
        }
        return subject.getWeeklyHours();
    }

    private boolean roomMatchesLabSubtype(Subject subject, Room room) {
        return subject.getLabSubtypeRequired() == null
            || subject.getLabSubtypeRequired().equals(room.getLabSubtype());
    }
}
