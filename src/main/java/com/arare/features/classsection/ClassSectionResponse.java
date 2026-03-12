package com.arare.features.classsection;

public record ClassSectionResponse(
    Long id,
    Long batchId,
    String batchName,
    String label,
    int size
) {}
