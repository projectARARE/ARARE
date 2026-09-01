package com.arare.features.schedule;

import com.arare.common.enums.SchoolDay;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.batch.BatchRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Renders a solved schedule as a printable PDF grid. The layout mirrors the
// on-screen timetable: rows are time slots, columns are days of the week.
// A view narrows the grid to one batch, teacher, or room, so the exported
// PDF is the timetable the end user actually cares about.
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PdfExportService.class);

    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(30, 41, 59));
    private static final Font SUB   = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 116, 139));
    private static final Font HEAD  = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TIME  = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(51, 65, 85));
    private static final Font CELL  = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, new Color(30, 41, 59));
    private static final Font CELL_B = new Font(Font.HELVETICA, 7.5f, Font.BOLD, new Color(30, 41, 59));

    private final ScheduleRepository     scheduleRepo;
    private final ClassSessionRepository sessionRepo;
    private final TimeslotRepository     timeslotRepo;
    private final TeacherRepository      teacherRepo;
    private final RoomRepository         roomRepo;
    private final BatchRepository        batchRepo;

    public enum View {
        ALL, TEACHER, BATCH, ROOM
    }

    // When a BATCH/TEACHER/ROOM view is selected without a single entity, the
    // export emits one full timetable per entity so the operator gets a separate
    // schedule for every batch (or teacher, or room), one PDF page each.
    @Transactional(readOnly = true)
    public byte[] exportPdf(Long scheduleId, View view, Long entityId) {
        com.arare.features.schedule.Schedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));

        List<Timeslot> classSlots = timeslotRepo.findByType(com.arare.common.enums.TimeslotType.CLASS)
                .stream()
                .sorted(Comparator
                        .comparing(Timeslot::getStartTime)
                        .thenComparing(Timeslot::getDay))
                .toList();

        List<SchoolDay> days = dayColumns(classSlots);

        List<ClassSession> allSessions = sessionRepo.findByScheduleId(scheduleId);
        long excludedNonClass = allSessions.stream()
                .filter(s -> s.getTimeslot() != null
                        && s.getTimeslot().getType() != com.arare.common.enums.TimeslotType.CLASS)
                .count();
        if (excludedNonClass > 0) {
            log.info("PDF export for schedule {} omitted {} session(s) whose timeslot is not CLASS.",
                    scheduleId, excludedNonClass);
        }
        List<ClassSession> placed = allSessions.stream()
                .filter(s -> s.getTimeslot() != null)
                .toList();

        boolean perEntity = view != View.ALL && entityId == null;
        List<Long> entityIds = perEntity ? entityIdsInView(view, placed) : List.of();

        Document doc = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            if (perEntity) {
                if (entityIds.isEmpty()) {
                    renderGrid(doc, schedule, schedule.getName(), classSlots, days,
                            placed, view);
                } else {
                    for (int i = 0; i < entityIds.size(); i++) {
                        if (i > 0) doc.newPage();
                        Long id = entityIds.get(i);
                        List<ClassSession> es = placed.stream()
                                .filter(s -> matches(s, view, id))
                                .toList();
                        String subtitle = schedule.getName() + "  ·  " + entityLabel(view, id);
                        renderGrid(doc, schedule, subtitle, classSlots, days, es, view);
                    }
                }
            } else {
                String entityName = entityLabel(view, entityId);
                String subtitle = entityName != null
                        ? schedule.getName() + "  ·  " + entityName
                        : schedule.getName();
                List<ClassSession> sessions = entityId == null
                        ? placed
                        : placed.stream().filter(s -> matches(s, view, entityId)).toList();
                renderGrid(doc, schedule, subtitle, classSlots, days, sessions, view);
            }

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render PDF timetable", e);
        }

        return out.toByteArray();
    }

    private void renderGrid(Document doc, com.arare.features.schedule.Schedule schedule,
                            String subtitle, List<Timeslot> classSlots, List<SchoolDay> days,
                            List<ClassSession> sessions, View view) throws DocumentException {
        Map<Long, List<ClassSession>> bySlot = new java.util.HashMap<>();
        for (ClassSession s : sessions) {
            bySlot.computeIfAbsent(s.getTimeslot().getId(), k -> new ArrayList<>()).add(s);
        }

        doc.add(new Paragraph(schedule.getName(), TITLE));
        doc.add(new Paragraph(subtitle, SUB));
        doc.add(new Paragraph("Generated " + java.time.LocalDateTime.now()
                + "  ·  " + sessions.size() + " session(s) shown", SUB));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(days.size() + 1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(12);
        float[] widths = new float[days.size() + 1];
        widths[0] = 1.6f;
        for (int i = 1; i < widths.length; i++) widths[i] = 1.0f;
        table.setWidths(widths);

        table.addCell(headerCell("Time"));
        for (SchoolDay d : days) table.addCell(headerCell(d.name()));

        for (Timeslot slot : classSlots) {
            table.addCell(timeCell(slot.getStartTime(), slot.getEndTime()));
            for (SchoolDay d : days) {
                List<ClassSession> cellSessions = bySlot.getOrDefault(slot.getId(), List.of())
                        .stream()
                        .filter(s -> s.getTimeslot().getDay() == d)
                        .toList();
                table.addCell(sessionCell(cellSessions, view));
            }
        }

        doc.add(table);
    }

    private List<Long> entityIdsInView(View view, List<ClassSession> sessions) {
        return switch (view) {
            case TEACHER -> sessions.stream()
                    .map(ClassSession::getTeacher)
                    .filter(java.util.Objects::nonNull)
                    .map(com.arare.features.teacher.Teacher::getId)
                    .distinct().sorted().toList();
            case BATCH -> sessions.stream()
                    .map(this::effectiveBatchId)
                    .filter(java.util.Objects::nonNull)
                    .distinct().sorted().toList();
            case ROOM -> sessions.stream()
                    .map(ClassSession::getRoom)
                    .filter(java.util.Objects::nonNull)
                    .map(com.arare.features.room.Room::getId)
                    .distinct().sorted().toList();
            case ALL -> List.of();
        };
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

    private String entityLabel(View view, Long entityId) {
        if (entityId == null) return null;
        return switch (view) {
            case TEACHER -> teacherRepo.findById(entityId)
                    .map(t -> "Teacher: " + t.getName())
                    .orElse(null);
            case BATCH -> batchRepo.findById(entityId)
                    .map(b -> {
                        String dept = b.getDepartment() != null ? b.getDepartment().getName() + " " : "";
                        return "Batch: " + dept + "Yr" + b.getYear() + "-" + b.getSection();
                    })
                    .orElse(null);
            case ROOM -> roomRepo.findById(entityId)
                    .map(r -> "Room: " + r.getRoomNumber() + (r.getBuilding() != null ? " (" + r.getBuilding().getName() + ")" : ""))
                    .orElse(null);
            case ALL -> null;
        };
    }

    private List<SchoolDay> dayColumns(List<Timeslot> slots) {
        return slots.stream()
                .map(Timeslot::getDay)
                .distinct()
                .sorted(Comparator.comparingInt(SchoolDay::ordinal))
                .collect(Collectors.toList());
    }

    private PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, HEAD));
        c.setBackgroundColor(new Color(79, 70, 229));
        c.setPadding(5);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private PdfPCell timeCell(LocalTime start, LocalTime end) {
        PdfPCell c = new PdfPCell(new Phrase(start + "-" + end, TIME));
        c.setPadding(4);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(new Color(241, 245, 249));
        return c;
    }

    private PdfPCell sessionCell(List<ClassSession> sessions, View view) {
        PdfPCell c = new PdfPCell();
        c.setPadding(3);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        if (sessions.isEmpty()) {
            c.setMinimumHeight(30);
            return c;
        }
        for (ClassSession s : sessions) {
            Paragraph p = new Paragraph();
            p.add(new Phrase(s.getSubject().getName() + "  ", CELL_B));
            if (view == View.TEACHER) {
                p.add(new Phrase(batchLabel(s), CELL));
            } else if (s.getTeacher() != null) {
                p.add(new Phrase("\n" + s.getTeacher().getName(), CELL));
            }
            if (s.getRoom() != null) {
                p.add(new Phrase("\n" + s.getRoom().getRoomNumber()
                        + (s.getRoom().getBuilding() != null ? " · " + s.getRoom().getBuilding().getName() : ""), CELL));
            }
            c.addElement(p);
            if (sessions.size() > 1) {
                c.addElement(new Paragraph(" ", CELL));
            }
        }
        c.setMinimumHeight(30);
        return c;
    }

    private String batchLabel(ClassSession s) {
        if (s.getSection() != null) {
            ClassSection sec = s.getSection();
            if (sec.getBatch() != null) {
                var b = sec.getBatch();
                return (b.getDepartment() != null ? b.getDepartment().getName() : "")
                        + " Yr" + b.getYear() + "-" + b.getSection() + " [" + sec.getLabel() + "]";
            }
            return sec.getLabel();
        }
        if (s.getBatch() != null) {
            var b = s.getBatch();
            return (b.getDepartment() != null ? b.getDepartment().getName() : "")
                    + " Yr" + b.getYear() + "-" + b.getSection();
        }
        return "";
    }
}