package com.arare.features.classsession;

public record ClassSessionResponse(
    Long id,
    Long subjectId,
    String subjectName,
    boolean isLab,
    /**
     * null for lab-section sessions
     */
    Long batchId,             
    /**
     * null for batch sessions
     */
    Long sectionId,           
    /**
     * e.g. "CSE-2A"  or section label for lab splits
     */
    String batchLabel,        
    Long teacherId,
    String teacherName,
    Long roomId,
    String roomNumber,
    String buildingName,
    Long timeslotId,
    String day,
    String startTime,
    String endTime,
    int duration,
    boolean isLocked
) {}
