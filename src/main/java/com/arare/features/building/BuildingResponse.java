package com.arare.features.building;

/** Response DTO returned to the client. */
public record BuildingResponse(
    Long id,
    String name,
    String location
) {
    public static BuildingResponse from(Building b) {
        return new BuildingResponse(b.getId(), b.getName(), b.getLocation());
    }
}
