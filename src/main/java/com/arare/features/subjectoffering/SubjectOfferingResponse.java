package com.arare.features.subjectoffering;

// Response DTO for a SubjectOffering.
public record SubjectOfferingResponse(
    Long id,
    Long subjectId,
    String subjectCode,
    String subjectName,
    Long batchId,
    String batchLabel,
    Long sectionId,
    String sectionLabel,
    Integer weeklyHours,
    boolean elective
) {}