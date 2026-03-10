package com.arare.features.building;

import java.util.List;

public interface BuildingService {
    BuildingResponse create(BuildingRequest request);
    BuildingResponse update(Long id, BuildingRequest request);
    BuildingResponse findById(Long id);
    List<BuildingResponse> findAll();
    void delete(Long id);
}
