package com.arare.features.classsection;

public record ClassSectionResponse(
    Long id,
    Long batchId,
    String label,
    int size
) {}
