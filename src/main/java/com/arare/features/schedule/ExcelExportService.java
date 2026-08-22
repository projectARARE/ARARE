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
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// Exports a solved schedule as an Excel workbook. The default ALL view emits one
// sheet per batch (lab sections grouped under their batch) so a class's full week
// is visible on a single tab. The TEACHER/BATCH/ROOM views emit a single sheet
// narrowed to that entity. Streaming POI (SXSSF) keeps memory flat even when the
// timetable has thousands of sessions.
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final ScheduleRepository     scheduleRepo;
    private final ClassSessionRepository sessionRepo;
    private final TimeslotRepository     timeslotRepo;
    private final TeacherRepository      teacherRepo;
    private final RoomRepository         roomRepo;
    private final BatchRepository        batchRepo;

    public enum View {
        ALL, TEACHER, BATCH, ROOM
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(Long scheduleId, View view, Long entityId) {
        com.arare.features.schedule.Schedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));

        List<Timeslot> classSlots = timeslotRepo.findByType(com.arare.common.enums.TimeslotType.CLASS)
                .stream()
                .sorted(Comparator.comparing(Timeslot::getStartTime))
                .toList();

        List<SchoolDay> days = dayColumns(classSlots);

        List<ClassSession> sessions = sessionRepo.findByScheduleId(scheduleId).stream()
                .filter(s -> s.getTimeslot() != null)
                .filter(s -> matches(s, view, entityId))
                .toList();

        try (XSSFWorkbook template = new XSSFWorkbook();
             SXSSFWorkbook wb = new SXSSFWorkbook(template, 500)) {

            Styles styles = new Styles(wb);

            if (view == View.ALL) {
                Map<String, List<ClassSession>> byBatch = groupByBatch(sessions);
                if (byBatch.isEmpty()) {
                    Sheet sheet = wb.createSheet("Timetable");
                    renderGrid(sheet, wb, styles, schedule, entityLabel(view, entityId), classSlots, days, sessions, view);
                } else {
                    byBatch.forEach((label, batchSessions) ->
                            renderGrid(wb.createSheet(safeSheetName(label)), wb, styles, schedule,
                                    label, classSlots, days, batchSessions, view));
                }
            } else {
                Sheet sheet = wb.createSheet("Timetable");
                renderGrid(sheet, wb, styles, schedule, entityLabel(view, entityId), classSlots, days, sessions, view);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render Excel timetable", e);
        }
    }

    // Renders an arbitrary header/row grid as a single-sheet workbook. Used by the
    // generic "Export Excel" action on every master-data table so each page supports
    // the same CSV + Excel export without per-page backend wiring.
    @Transactional(readOnly = true)
    public byte[] exportRows(String sheetName, List<String> headers, List<List<String>> rows) {
        try (XSSFWorkbook template = new XSSFWorkbook();
             SXSSFWorkbook wb = new SXSSFWorkbook(template, 500)) {

            Sheet sheet = wb.createSheet(safeSheetName(sheetName == null ? "Export" : sheetName));
            sheet.setDefaultColumnWidth(20);
            sheet.createFreezePane(0, 1);

            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(new Styles(wb).head());
            }

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.createCell(c);
                    String value = c < values.size() ? values.get(c) : "";
                    if (value != null && !value.isBlank() && isNumeric(value)) {
                        cell.setCellValue(Double.parseDouble(value.replace(",", "")));
                    } else {
                        cell.setCellValue(value == null ? "" : value);
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render Excel export", e);
        }
    }

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s.replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void renderGrid(Sheet sheet, SXSSFWorkbook wb, Styles styles, com.arare.features.schedule.Schedule schedule,
                            String subtitle, List<Timeslot> classSlots, List<SchoolDay> days,
                            List<ClassSession> sessions, View view) {
        sheet.setDefaultColumnWidth(22);
        sheet.createFreezePane(1, 4);

        int row = 0;
        Row title = sheet.createRow(row++);
        Cell tc = title.createCell(0);
        tc.setCellValue(schedule.getName());
        tc.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, days.size()));

        Row sub = sheet.createRow(row++);
        Cell sc = sub.createCell(0);
        sc.setCellValue((subtitle != null ? subtitle + " · " : "") + sessions.size() + " session(s) · generated "
                + java.time.LocalDateTime.now());
        sc.setCellStyle(styles.sub);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, days.size()));

        Row blank = sheet.createRow(row++);

        Row header = sheet.createRow(row++);
        Cell hc = header.createCell(0);
        hc.setCellValue("Time");
        hc.setCellStyle(styles.head);
        for (int i = 0; i < days.size(); i++) {
            Cell c = header.createCell(i + 1);
            c.setCellValue(days.get(i).name());
            c.setCellStyle(styles.head);
        }

        Map<Long, List<ClassSession>> bySlot = new java.util.HashMap<>();
        for (ClassSession s : sessions) {
            bySlot.computeIfAbsent(s.getTimeslot().getId(), k -> new ArrayList<>()).add(s);
        }

        for (Timeslot slot : classSlots) {
            Row r = sheet.createRow(row++);
            Cell timeCell = r.createCell(0);
            timeCell.setCellValue(slot.getStartTime() + "-" + slot.getEndTime());
            timeCell.setCellStyle(styles.time);
            for (int i = 0; i < days.size(); i++) {
                SchoolDay d = days.get(i);
                Cell c = r.createCell(i + 1);
                if (slot.getDay() != d) {
                    c.setCellStyle(styles.empty);
                    continue;
                }
                List<ClassSession> cellSessions = bySlot.getOrDefault(slot.getId(), List.of());
                c.setCellValue(sessionText(cellSessions, view));
                c.setCellStyle(cellSessions.isEmpty() ? styles.empty : styles.cell);
            }
        }
    }

    private String sessionText(List<ClassSession> sessions, View view) {
        if (sessions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sessions.size(); i++) {
            ClassSession s = sessions.get(i);
            if (i > 0) sb.append(" | ");
            sb.append(s.getSubject().getName());
            if (view == View.TEACHER) {
                sb.append("  ").append(batchLabel(s));
            } else if (s.getTeacher() != null) {
                sb.append("  ").append(s.getTeacher().getName());
            }
            if (s.getRoom() != null) {
                sb.append("  ").append(s.getRoom().getRoomNumber());
                if (s.getRoom().getBuilding() != null) {
                    sb.append(" · ").append(s.getRoom().getBuilding().getName());
                }
            }
        }
        return sb.toString();
    }

    private Map<String, List<ClassSession>> groupByBatch(List<ClassSession> sessions) {
        Map<String, List<ClassSession>> map = new java.util.LinkedHashMap<>();
        for (ClassSession s : sessions) {
            map.computeIfAbsent(batchLabel(s), k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private String safeSheetName(String label) {
        String cleaned = label.replaceAll("[\\\\/*?:\\[\\]]", "-").trim();
        if (cleaned.isEmpty()) cleaned = "Timetable";
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
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
        LinkedHashSet<SchoolDay> days = new LinkedHashSet<>();
        for (Timeslot t : slots) days.add(t.getDay());
        return new ArrayList<>(days);
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
        return "Unassigned";
    }

    private record Styles(CellStyle title, CellStyle sub, CellStyle head, CellStyle time, CellStyle cell, CellStyle empty) {
        Styles(SXSSFWorkbook wb) {
            this(
                    style(wb, true, 14, IndexedColors.INDIGO.getIndex(), HorizontalAlignment.LEFT),
                    style(wb, false, 10, IndexedColors.GREY_50_PERCENT.getIndex(), HorizontalAlignment.LEFT),
                    style(wb, true, 11, IndexedColors.INDIGO.getIndex(), HorizontalAlignment.CENTER),
                    style(wb, true, 11, IndexedColors.GREY_25_PERCENT.getIndex(), HorizontalAlignment.LEFT),
                    style(wb, false, 10, IndexedColors.WHITE.getIndex(), HorizontalAlignment.LEFT),
                    style(wb, false, 10, IndexedColors.GREY_25_PERCENT.getIndex(), HorizontalAlignment.LEFT)
            );
        }
    }

    private static CellStyle style(SXSSFWorkbook wb, boolean bold, int fontSize,
                                   short fill, HorizontalAlignment align) {
        CellStyle cs = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        cs.setFont(font);
        cs.setFillForegroundColor(fill);
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cs.setAlignment(align);
        cs.setWrapText(true);
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
        return cs;
    }
}