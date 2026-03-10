package com.arare.features.room;

import java.util.List;

public interface RoomService {
    RoomResponse create(RoomRequest request);
    RoomResponse update(Long id, RoomRequest request);
    RoomResponse findById(Long id);
    List<RoomResponse> findAll();
    List<RoomResponse> findByBuilding(Long buildingId);
    void delete(Long id);
}
