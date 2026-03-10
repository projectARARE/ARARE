package com.arare.features.building;

import jakarta.validation.constraints.NotBlank;

/** Request DTO: create or update a Building. */
public record BuildingRequest(
    @NotBlank String name,
    String location
) {}
