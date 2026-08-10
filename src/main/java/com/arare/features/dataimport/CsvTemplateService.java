package com.arare.features.dataimport;

import org.springframework.stereotype.Service;

import java.util.List;

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
                new String[]{"code", "name", "buildingNames"},
                List.of(
                    List.of("CSE", "Computer Science", "Main Block|Science Wing"),
                    List.of("ECE", "Electronics and Communication", "Science Wing|Innovation Hub"),
                    List.of("MEC", "Mechanical Engineering", "Innovation Hub")
                )
            );
            case ROOMS -> CsvUtils.write(
                new String[]{"buildingName", "roomNumber", "type", "labSubtype", "capacity", "availableTimeslots"},
                List.of(
                    List.of("Main Block", "101", "LECTURE", "", "60", "MONDAY@08:00-09:00|MONDAY@09:00-10:00"),
                    List.of("Science Wing", "LAB-1", "LAB", "COMPUTER_LAB", "30", "MONDAY@09:00-10:00|TUESDAY@08:00-09:00"),
                    List.of("Innovation Hub", "201", "LECTURE", "", "45", "WEDNESDAY@11:00-12:00"),
                    List.of("Innovation Hub", "LAB-2", "LAB", "ELECTRONICS_LAB", "24", "TUESDAY@10:00-10:30|WEDNESDAY@11:00-12:00")
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
                new String[]{"employeeId", "name", "maxDailyHours", "maxWeeklyHours", "maxConsecutiveClasses", "movementPenalty", "preferredFreeDay", "subjectCodes", "availableTimeslots", "preferredBuildingNames"},
                List.of(
                    List.of("EMP001", "Dr. Jane Smith", "6", "20", "3", "1", "FRIDAY", "CSE:CS101|CSE:CS205", "MONDAY@08:00-09:00|MONDAY@09:00-10:00", "Main Block|Innovation Hub"),
                    List.of("EMP002", "Prof. Arun Kumar", "5", "18", "2", "2", "MONDAY", "ECE:EC201", "TUESDAY@08:00-09:00|TUESDAY@10:00-10:30", "Science Wing"),
                    List.of("EMP003", "Dr. Sara Khan", "6", "24", "3", "1", "WEDNESDAY", "MEC:ME301", "MONDAY@08:00-09:00|WEDNESDAY@11:00-12:00", "Innovation Hub|Main Block")
                )
            );
            case BATCHES -> CsvUtils.write(
                new String[]{"departmentCode", "year", "section", "studentCount", "preferredFreeDay", "workingDays"},
                List.of(
                    List.of("CSE", "1", "A", "60", "FRIDAY", "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|SATURDAY"),
                    List.of("ECE", "2", "B", "48", "MONDAY", "MONDAY|WEDNESDAY|FRIDAY"),
                    List.of("MEC", "3", "C", "52", "THURSDAY", "TUESDAY|THURSDAY|SATURDAY")
                )
            );
        };
    }

    public String templateFileName(CsvEntityType entityType) {
        return entityType.getFileName().replace(".csv", "-template.csv");
    }
}