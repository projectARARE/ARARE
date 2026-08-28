package com.arare.features.building;

// Response DTO returned to the client. 
public record BuildingResponse(
    Long id,
    String name,
    String location
) {
}
