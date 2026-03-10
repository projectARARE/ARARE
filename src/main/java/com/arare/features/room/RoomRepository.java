package com.arare.features.room;

import com.arare.common.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByBuildingId(Long buildingId);

    List<Room> findByType(RoomType type);

    /** Find rooms with enough capacity for a given student count. */
    List<Room> findByCapacityGreaterThanEqual(int studentCount);

    /** Find lecture rooms with enough capacity. */
    List<Room> findByTypeAndCapacityGreaterThanEqual(RoomType type, int minCapacity);
}
