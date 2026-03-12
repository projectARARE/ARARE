package com.arare.features.schedule;

import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * Exports a solved schedule to CSV format.
 *
 * <p>The CSV output is a flat list of assigned sessions ordered by
 * day-of-week then start time, suitable for import into Excel or
 * any spreadsheet application.  An UTF-8 BOM is prepended so Excel
 * opens the file with the correct encoding without a manual import step.</p>
 */
@Service
@RequiredArgsConstructor
public class TimetableExportService {

    private final ScheduleRepository     scheduleRepo;
    private final ClassSessionRepository sessionRepo;

    @Transactional(readOnly = true)
    public byte[] exportCsv(Long scheduleId) {
        scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));

        List<ClassSession> sessions = sessionRepo.findByScheduleId(scheduleId);

        StringBuilder sb = new StringBuilder("\uFEFF"); // UTF-8 BOM for Excel
        sb.append("Day,Start,End,Subject,Code,Teacher,Room,Building,Batch,Section,Type,Duration(h),Locked\n");

        sessions.stream()
                .filter(s -> s.getTimeslot() != null)
                .sorted(Comparator
                        .<ClassSession, Integer>comparing(s -> s.getTimeslot().getDay().ordinal())
                        .thenComparing(s -> s.getTimeslot().getStartTime()))
                .forEach(s -> {
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
                });

        long unassigned = sessions.stream().filter(s -> s.getTimeslot() == null).count();
        if (unassigned > 0) {
            sb.append('\n')
              .append(csv("# " + unassigned + " session(s) were not assigned a timeslot and are excluded."))
              .append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }
}
