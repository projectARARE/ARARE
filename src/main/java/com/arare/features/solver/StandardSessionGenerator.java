package com.arare.features.solver;

import com.arare.common.enums.RoomType;
import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.schedule.Schedule;
import com.arare.features.subject.Subject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StandardSessionGenerator implements SessionGenerator {

    @Override
    public List<ClassSession> generate(
        Schedule schedule,
        List<Subject> subjects,
        List<Batch> batches,
        List<ClassSection> sections,
        List<Room> rooms
    ) {
        List<ClassSession> generated = new ArrayList<>();

        List<Subject> subjectsByChunkFirst = subjects.stream()
            .sorted(Comparator.comparingInt(Subject::getChunkHours).reversed())
            .toList();

        for (Batch batch : batches) {
            for (Subject subject : subjectsByChunkFirst) {
                if (!subject.getDepartment().getId().equals(batch.getDepartment().getId())) {
                    continue;
                }

                validateSubjectChunking(subject);
                int count = subject.getWeeklyHours() / subject.getChunkHours();

                if (subject.isLab()) {
                    generateLabSessions(generated, schedule, subject, batch, sections, rooms, count);
                } else {
                    generateLectureSessions(generated, schedule, subject, batch, count);
                }
            }
        }

        return generated;
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
        int count
    ) {
        boolean canRunWholeBatch = rooms.stream().anyMatch(room ->
            room.getType() == RoomType.LAB
                && room.getCapacity() >= batch.getStudentCount()
                && roomMatchesLabSubtype(subject, room)
        );

        List<ClassSection> batchSections = sections.stream()
            .filter(s -> s.getBatch().getId().equals(batch.getId()))
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

    private boolean roomMatchesLabSubtype(Subject subject, Room room) {
        return subject.getLabSubtypeRequired() == null
            || subject.getLabSubtypeRequired().equals(room.getLabSubtype());
    }
}
