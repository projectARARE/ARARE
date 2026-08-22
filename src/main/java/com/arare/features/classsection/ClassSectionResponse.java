package com.arare.features.classsection;

import java.util.List;

public record ClassSectionResponse(
    Long id,
    Long batchId,
    String batchName,
    String label,
    int size,
    List<Long> subjectIds,
    List<String> subjectNames
) {}
