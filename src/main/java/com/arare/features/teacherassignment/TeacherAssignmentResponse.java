package com.arare.features.teacherassignment;

// Read model returned by the API. batchLabel/sectionLabel are human-readable
// keys (e.g. "CSE-2A", "CSE-2A-A") so the UI never has to join across tables.
public record TeacherAssignmentResponse(
    Long id,
    Long teacherId,
    String teacherName,
    Long subjectId,
    String subjectCode,
    String subjectName,
    Long batchId,
    String batchLabel,
    Long sectionId,
    String sectionLabel,
    Integer weeklyHours,
    int priority,
    String notes
) {
}