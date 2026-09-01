package com.arare.features.schedule;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Exports a solved schedule to CSV. The output is a flat list of assigned
// sessions ordered by day-of-week then start time, with a UTF-8 BOM so Excel opens
// it correctly. When a BATCH/TEACHER/ROOM view is active with no single entity,
// one CSV file per entity is returned inside a ZIP so each batch (or teacher, or
// room) gets its own schedule file.
@Service
@RequiredArgsConstructor
public class TimetableExportService {

    public enum View {
        ALL, TEACHER, BATCH, ROOM
    }

    // Sentinel key for sessions that have no applicable entity in the active view
    // dimension (e.g. a session with no teacher in a TEACHER split).
    private static final long UNASSIGNED = -1L;

    private final ScheduleRepository     scheduleRepo;
    private final ClassSessionRepository sessionRepo;
    private final BatchRepository        batchRepo;
    private final TeacherRepository      teacherRepo;
    private final RoomRepository         roomRepo;

    @Transactional(readOnly = true)
    public byte[] exportCsv(Long scheduleId, View view, Long entityId) {
        scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));

        List<ClassSession> allSessions = sessionRepo.findByScheduleId(scheduleId);
        List<ClassSession> placed = allSessions.stream()
                .filter(s -> s.getTimeslot() != null)
                .sorted(Comparator
                        .<ClassSession, Integer>comparing(s -> s.getTimeslot().getDay().ordinal())
                        .thenComparing(s -> s.getTimeslot().getStartTime()))
                .toList();

        boolean perEntity = view != View.ALL && entityId == null;

        if (perEntity) {
            Map<Long, List<ClassSession>> groups = groupByEntityId(placed, view);
            if (groups.isEmpty()) {
                return asCsv(placed);
            }
            return asZip(groups, view);
        }

        List<ClassSession> sessions = entityId == null
                ? placed
                : placed.stream().filter(s -> matches(s, view, entityId)).toList();
        return asCsv(sessions);
    }

    private byte[] asCsv(List<ClassSession> sessions) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Day,Start,End,Subject,Code,Teacher,Room,Building,Batch,Section,Type,Duration(h),Locked\n");
        for (ClassSession s : sessions) {
            appendRow(sb, s);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] asZip(Map<Long, List<ClassSession>> groups, View view) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<Long, List<ClassSession>> e : groups.entrySet()) {
                String name = e.getKey() == UNASSIGNED ? "Unassigned" : entityName(view, e.getKey());
                ZipEntry entry = new ZipEntry(safeFileName(name) + ".csv");
                zip.putNextEntry(entry);
                zip.write(asCsv(e.getValue()));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render CSV timetable ZIP", e);
        }
    }

    private void appendRow(StringBuilder sb, ClassSession s) {
        String batchName  = "";
        String sectionLbl = "";

        if (s.getSection() != null) {
            ClassSection sec = s.getSection();
            sectionLbl = sec.getLabel();
            if (sec.getBatch() != null) {
                var b = sec.getBatch();
                batchName = (b.getDepartment() != null ? b.getDepartment().getName() : "")
                        + " Yr" + b.getYear() + "-" + b.getSection();
            }
        } else if (s.getBatch() != null) {
            var b = s.getBatch();
            batchName = (b.getDepartment() != null ? b.getDepartment().getName() : "")
                    + " Yr" + b.getYear() + "-" + b.getSection();
            sectionLbl = b.getSection();
        }

        String buildingName = "";
        if (s.getRoom() != null && s.getRoom().getBuilding() != null) {
            buildingName = s.getRoom().getBuilding().getName();
        }

        sb.append(String.join(",",
                csv(s.getTimeslot().getDay().name()),
                csv(s.getTimeslot().getStartTime().toString()),
                csv(s.getTimeslot().getEndTime().toString()),
                csv(s.getSubject().getName()),
                csv(s.getSubject().getCode()),
                csv(s.getTeacher() != null ? s.getTeacher().getName() : ""),
                csv(s.getRoom()    != null ? s.getRoom().getRoomNumber() : ""),
                csv(buildingName),
                csv(batchName),
                csv(sectionLbl),
                csv(s.getSubject().isLab() ? "Lab" : "Lecture"),
                String.valueOf(s.getDuration()),
                s.isLocked() ? "Yes" : "No"
        )).append('\n');
    }

    // Buckets placed sessions by the entity's database id (teacher id, room id,
    // batch id) so that distinct entities with identical display labels — e.g.
    // two teachers with the same name, or the same "CSE Yr1-A" in two campuses —
    // still get their own separate CSV file. Sessions with no entity in the
    // active view fall into a single "Unassigned" bucket.
    private Map<Long, List<ClassSession>> groupByEntityId(List<ClassSession> sessions, View view) {
        Map<Long, List<ClassSession>> map = new LinkedHashMap<>();
        for (ClassSession s : sessions) {
            Long key = entityKey(s, view);
            map.computeIfAbsent(key == null ? UNASSIGNED : key, k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private Long entityKey(ClassSession s, View view) {
        return switch (view) {
            case TEACHER -> s.getTeacher() != null ? s.getTeacher().getId() : null;
            case ROOM -> s.getRoom() != null ? s.getRoom().getId() : null;
            case BATCH -> effectiveBatchId(s);
            case ALL -> UNASSIGNED;
        };
    }

    private String entityName(View view, Long id) {
        return switch (view) {
            case TEACHER -> teacherRepo.findById(id)
                    .map(Teacher::getName)
                    .orElse("Teacher " + id);
            case ROOM -> roomRepo.findById(id)
                    .map(r -> r.getRoomNumber()
                            + (r.getBuilding() != null ? " (" + r.getBuilding().getName() + ")" : ""))
                    .orElse("Room " + id);
            case BATCH -> batchRepo.findById(id)
                    .map(this::batchEntityName)
                    .orElse("Batch " + id);
            case ALL -> "Timetable";
        };
    }

    private String batchEntityName(Batch b) {
        String dept = b.getDepartment() != null ? b.getDepartment().getName() + " " : "";
        return dept + "Yr" + b.getYear() + "-" + b.getSection();
    }

    private boolean matches(ClassSession s, View view, Long entityId) {
        if (entityId == null) return true;
        return switch (view) {
            case TEACHER -> s.getTeacher() != null && s.getTeacher().getId().equals(entityId);
            case BATCH -> {
                Long batchId = effectiveBatchId(s);
                yield batchId != null && batchId.equals(entityId);
            }
            case ROOM -> s.getRoom() != null && s.getRoom().getId().equals(entityId);
            case ALL -> true;
        };
    }

    private Long effectiveBatchId(ClassSession s) {
        if (s.getBatch() != null) return s.getBatch().getId();
        if (s.getSection() != null && s.getSection().getBatch() != null) {
            return s.getSection().getBatch().getId();
        }
        return null;
    }

    // Keeps a per-entity CSV file name short and free of filesystem-hostile
    // characters while staying readable (e.g. "CSE Yr1-A.csv").
    private String safeFileName(String label) {
        String cleaned = label.replaceAll("[\\\\/*?:\\[\\]<>|\"]", "-").trim();
        if (cleaned.isEmpty()) cleaned = "timetable";
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    private static String csv(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }
}
