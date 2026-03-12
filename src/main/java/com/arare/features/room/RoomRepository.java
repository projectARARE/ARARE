package com.arare.features.room;

import com.arare.common.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByBuildingId(Long buildingId);

    List<Room> findByType(RoomType type);

    List<Room> findByCapacityGreaterThanEqual(int studentCount);

    List<Room> findByTypeAndCapacityGreaterThanEqual(RoomType type, int minCapacity);

    @Transactional @Modifying
    @Query("DELETE FROM Room r WHERE r.building.id = :buildingId")
    void deleteByBuildingId(@Param("buildingId") Long buildingId);
}
