package com.arare.features.dataimport;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fixed sample templates for the single-entity CSV import path. Every column
 * holds a single value per row; multi-valued relationships are expressed in
 * the separate pairing files (see {@link #relationshipTemplate(String)}), so
 * no cell ever contains a {@code |}-separated list.
 */
@Service
public class CsvTemplateService {

    public String exportTemplateCsv(CsvEntityType entityType) {
        return switch (entityType) {
            case TIMESLOTS -> CsvUtils.write(
                new String[]{"day", "startTime", "endTime", "slotNumber", "type"},
                List.of(
                    List.of("MONDAY", "08:00", "09:00", "1", "CLASS"),
                    List.of("MONDAY", "09:00", "10:00", "2", "CLASS"),
                    List.of("TUESDAY", "10:00", "10:30", "3", "BREAK"),
                    List.of("WEDNESDAY", "11:00", "12:00", "4", "BLOCKED")
                )
            );
            case BUILDINGS -> CsvUtils.write(
                new String[]{"name", "location"},
                List.of(
                    List.of("Main Block", "North Campus"),
                    List.of("Science Wing", "East Campus"),
                    List.of("Innovation Hub", "South Campus")
                )
            );
            case DEPARTMENTS -> CsvUtils.write(
                new String[]{"code", "name"},
                List.of(
                    List.of("CSE", "Computer Science"),
                    List.of("ECE", "Electronics and Communication"),
                    List.of("MEC", "Mechanical Engineering")
                )
            );
            case ROOMS -> CsvUtils.write(
                new String[]{"buildingName", "roomNumber", "type", "labSubtype", "capacity"},
                List.of(
                    List.of("Main Block", "101", "LECTURE", "", "60"),
                    List.of("Science Wing", "LAB-1", "LAB", "COMPUTER_LAB", "30"),
                    List.of("Innovation Hub", "201", "LECTURE", "", "45"),
                    List.of("Innovation Hub", "LAB-2", "LAB", "ELECTRONICS_LAB", "24")
                )
            );
            case SUBJECTS -> CsvUtils.write(
                new String[]{"departmentCode", "code", "name", "weeklyHours", "chunkHours", "roomTypeRequired", "labSubtypeRequired", "isLab", "requiresTeacher", "requiresRoom", "minGapBetweenSessions", "maxSessionsPerDay"},
                List.of(
                    List.of("CSE", "CS101", "Programming Fundamentals", "4", "2", "LECTURE", "", "false", "true", "true", "1", "2"),
                    List.of("CSE", "CS205", "Database Systems", "3", "1", "LECTURE", "", "false", "true", "true", "1", "2"),
                    List.of("ECE", "EC201", "Digital Logic Lab", "4", "2", "LAB", "COMPUTER_LAB", "true", "true", "true", "1", "1"),
                    List.of("MEC", "ME301", "Thermodynamics", "3", "1", "LECTURE", "", "false", "true", "true", "1", "2")
                )
            );
            case TEACHERS -> CsvUtils.write(
                new String[]{"employeeId", "name", "maxDailyHours", "maxWeeklyHours", "maxConsecutiveClasses", "movementPenalty", "preferredFreeDay"},
                List.of(
                    List.of("EMP001", "Dr. Jane Smith", "6", "20", "3", "1", "FRIDAY"),
                    List.of("EMP002", "Prof. Arun Kumar", "5", "18", "2", "2", "MONDAY"),
                    List.of("EMP003", "Dr. Sara Khan", "6", "24", "3", "1", "WEDNESDAY")
                )
            );
            case BATCHES -> CsvUtils.write(
                new String[]{"departmentCode", "year", "section", "studentCount", "preferredFreeDay"},
                List.of(
                    List.of("CSE", "1", "A", "60", "FRIDAY"),
                    List.of("ECE", "2", "B", "48", "MONDAY"),
                    List.of("MEC", "3", "C", "52", "THURSDAY")
                )
            );
        };
    }

    /** Sample for one of the normalized relationship pairing files. */
    public String relationshipTemplate(String filename) {
        return switch (filename) {
            case "dept_buildings.csv" -> CsvUtils.write(
                new String[]{"departmentCode", "buildingName"},
                List.of(
                    List.of("CSE", "Main Block"),
                    List.of("CSE", "Science Wing"),
                    List.of("ECE", "Science Wing"),
                    List.of("ECE", "Innovation Hub"),
                    List.of("MEC", "Innovation Hub")
                )
            );
            case "teacher_subjects.csv" -> CsvUtils.write(
                new String[]{"employeeId", "departmentCode", "subjectCode"},
                List.of(
                    List.of("EMP001", "CSE", "CS101"),
                    List.of("EMP001", "CSE", "CS205"),
                    List.of("EMP002", "ECE", "EC201"),
                    List.of("EMP003", "MEC", "ME301")
                )
            );
            case "teacher_availability.csv" -> CsvUtils.write(
                new String[]{"employeeId", "day", "startTime", "endTime"},
                List.of(
                    List.of("EMP001", "MONDAY", "08:00", "09:00"),
                    List.of("EMP001", "MONDAY", "09:00", "10:00"),
                    List.of("EMP002", "TUESDAY", "08:00", "09:00"),
                    List.of("EMP002", "TUESDAY", "10:00", "10:30"),
                    List.of("EMP003", "MONDAY", "08:00", "09:00"),
                    List.of("EMP003", "WEDNESDAY", "11:00", "12:00")
                )
            );
            case "teacher_preferred_buildings.csv" -> CsvUtils.write(
                new String[]{"employeeId", "buildingName"},
                List.of(
                    List.of("EMP001", "Main Block"),
                    List.of("EMP001", "Innovation Hub"),
                    List.of("EMP002", "Science Wing"),
                    List.of("EMP003", "Innovation Hub"),
                    List.of("EMP003", "Main Block")
                )
            );
            case "room_availability.csv" -> CsvUtils.write(
                new String[]{"buildingName", "roomNumber", "day", "startTime", "endTime"},
                List.of(
                    List.of("Main Block", "101", "MONDAY", "08:00", "09:00"),
                    List.of("Main Block", "101", "MONDAY", "09:00", "10:00"),
                    List.of("Science Wing", "LAB-1", "MONDAY", "09:00", "10:00"),
                    List.of("Science Wing", "LAB-1", "TUESDAY", "08:00", "09:00"),
                    List.of("Innovation Hub", "201", "WEDNESDAY", "11:00", "12:00"),
                    List.of("Innovation Hub", "LAB-2", "TUESDAY", "10:00", "10:30"),
                    List.of("Innovation Hub", "LAB-2", "WEDNESDAY", "11:00", "12:00")
                )
            );
            case "batch_working_days.csv" -> CsvUtils.write(
                new String[]{"departmentCode", "year", "section", "day"},
                List.of(
                    List.of("CSE", "1", "A", "MONDAY"),
                    List.of("CSE", "1", "A", "TUESDAY"),
                    List.of("CSE", "1", "A", "WEDNESDAY"),
                    List.of("CSE", "1", "A", "THURSDAY"),
                    List.of("CSE", "1", "A", "SATURDAY"),
                    List.of("ECE", "2", "B", "MONDAY"),
                    List.of("ECE", "2", "B", "WEDNESDAY"),
                    List.of("ECE", "2", "B", "FRIDAY"),
                    List.of("MEC", "3", "C", "TUESDAY"),
                    List.of("MEC", "3", "C", "THURSDAY"),
                    List.of("MEC", "3", "C", "SATURDAY")
                )
            );
            case "config_working_days.csv" -> CsvUtils.write(
                new String[]{"day"},
                List.of(
                    List.of("MONDAY"),
                    List.of("TUESDAY"),
                    List.of("WEDNESDAY"),
                    List.of("THURSDAY"),
                    List.of("FRIDAY"),
                    List.of("SATURDAY")
                )
            );
            case "config_break_indices.csv" -> CsvUtils.write(
                new String[]{"index"},
                List.of(
                    List.of("3")
                )
            );
            default -> "";
        };
    }

    public List<String> relationshipFileNames() {
        return List.of(
            "dept_buildings.csv", "teacher_subjects.csv", "teacher_availability.csv",
            "teacher_preferred_buildings.csv", "room_availability.csv",
            "batch_working_days.csv", "config_working_days.csv", "config_break_indices.csv"
        );
    }

    public String templateFileName(CsvEntityType entityType) {
        return entityType.getFileName().replace(".csv", "-template.csv");
    }
}