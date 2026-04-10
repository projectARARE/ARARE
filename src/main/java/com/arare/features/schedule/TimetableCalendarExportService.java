package com.arare.features.schedule;

import com.arare.common.enums.ScheduleStatus;
import com.arare.common.enums.SchoolDay;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.batch.BatchRepository;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimetableCalendarExportService {

    private static final int DEFAULT_WEEK_COUNT = 16;
    private static final DateTimeFormatter LOCAL_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final ScheduleRepository scheduleRepo;
    private final ClassSessionRepository sessionRepo;
    private final TeacherRepository teacherRepo;
    private final BatchRepository batchRepo;

    @Transactional(readOnly = true)
    public byte[] exportTeacherCalendar(Long teacherId, Long scheduleId) {
        teacherRepo.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("Teacher", teacherId));

        Schedule schedule = resolveSchedule(scheduleId);
        List<ClassSession> sessions = sessionRepo.findByScheduleId(schedule.getId()).stream()
            .filter(s -> s.getTimeslot() != null
                && s.getTeacher() != null
                && s.getTeacher().getId().equals(teacherId))
            .sorted(Comparator
                .<ClassSession, Integer>comparing(s -> s.getTimeslot().getDay().ordinal())
                .thenComparing(s -> s.getTimeslot().getStartTime()))
            .toList();

        String calName = "ARARE Teacher " + teacherId;
        String ics = buildCalendar(schedule.getId(), calName, sessions, "teacher-" + teacherId);
        return ics.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportBatchCalendar(Long batchId, Long scheduleId) {
        batchRepo.findById(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        Schedule schedule = resolveSchedule(scheduleId);
        List<ClassSession> sessions = sessionRepo.findByScheduleId(schedule.getId()).stream()
            .filter(s -> s.getTimeslot() != null && belongsToBatch(s, batchId))
            .sorted(Comparator
                .<ClassSession, Integer>comparing(s -> s.getTimeslot().getDay().ordinal())
                .thenComparing(s -> s.getTimeslot().getStartTime()))
            .toList();

        String calName = "ARARE Batch " + batchId;
        String ics = buildCalendar(schedule.getId(), calName, sessions, "batch-" + batchId);
        return ics.getBytes(StandardCharsets.UTF_8);
    }

    private Schedule resolveSchedule(Long scheduleId) {
        if (scheduleId != null) {
            return scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));
        }

        return scheduleRepo.findTopByStatusOrderByCreatedAtDesc(ScheduleStatus.ACTIVE)
            .or(() -> scheduleRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().findFirst())
            .orElseThrow(() -> new IllegalStateException("No schedule available for calendar export."));
    }

    private String buildCalendar(Long scheduleId, String calName, List<ClassSession> sessions, String uidPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//ARARE//Timetable//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:").append(escapeIcs(calName)).append("\r\n");

        String nowStamp = UTC_DT.format(LocalDateTime.now(ZoneOffset.UTC));
        for (ClassSession s : sessions) {
            LocalDate startDate = nextOrSameDate(s.getTimeslot().getDay());
            LocalDateTime start = LocalDateTime.of(startDate, s.getTimeslot().getStartTime());
            LocalDateTime end = LocalDateTime.of(startDate, s.getTimeslot().getEndTime());

            String summary = s.getSubject().getName();
            if (s.getBatch() != null) {
                summary += " - Yr" + s.getBatch().getYear() + s.getBatch().getSection();
            } else if (s.getSection() != null) {
                summary += " - " + s.getSection().getLabel();
            }

            String roomPart = s.getRoom() != null ? s.getRoom().getRoomNumber() : "Unassigned Room";
            String buildingPart = (s.getRoom() != null && s.getRoom().getBuilding() != null)
                ? s.getRoom().getBuilding().getName()
                : "";
            String location = buildingPart.isBlank() ? roomPart : roomPart + " (" + buildingPart + ")";

            String description = "Schedule " + scheduleId
                + "\\nTeacher: " + (s.getTeacher() != null ? s.getTeacher().getName() : "Unassigned")
                + "\\nDuration: " + s.getDuration() + "h";

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:")
                .append(uidPrefix)
                .append("-schedule-")
                .append(scheduleId)
                .append("-session-")
                .append(s.getId())
                .append("@arare\r\n");
            sb.append("DTSTAMP:").append(nowStamp).append("\r\n");
            sb.append("DTSTART:").append(LOCAL_DT.format(start)).append("\r\n");
            sb.append("DTEND:").append(LOCAL_DT.format(end)).append("\r\n");
            sb.append("RRULE:FREQ=WEEKLY;COUNT=").append(DEFAULT_WEEK_COUNT).append("\r\n");
            sb.append("SUMMARY:").append(escapeIcs(summary)).append("\r\n");
            sb.append("LOCATION:").append(escapeIcs(location)).append("\r\n");
            sb.append("DESCRIPTION:").append(escapeIcs(description)).append("\r\n");
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static LocalDate nextOrSameDate(SchoolDay schoolDay) {
        DayOfWeek day = switch (schoolDay) {
            case MONDAY -> DayOfWeek.MONDAY;
            case TUESDAY -> DayOfWeek.TUESDAY;
            case WEDNESDAY -> DayOfWeek.WEDNESDAY;
            case THURSDAY -> DayOfWeek.THURSDAY;
            case FRIDAY -> DayOfWeek.FRIDAY;
            case SATURDAY -> DayOfWeek.SATURDAY;
        };
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(day));
    }

    private static boolean belongsToBatch(ClassSession s, Long batchId) {
        if (s.getBatch() != null && s.getBatch().getId().equals(batchId)) {
            return true;
        }
        return s.getSection() != null
            && s.getSection().getBatch() != null
            && s.getSection().getBatch().getId().equals(batchId);
    }

    private static String escapeIcs(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n");
    }
}
