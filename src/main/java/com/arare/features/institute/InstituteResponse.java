package com.arare.features.institute;

public record InstituteResponse(
    Long id,
    String name,
    String code,
    String description,
    int departmentCount
) {}
