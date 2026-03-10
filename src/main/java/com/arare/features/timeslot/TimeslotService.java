package com.arare.features.timeslot;

import java.util.List;

public interface TimeslotService {
    TimeslotResponse create(TimeslotRequest request);
    TimeslotResponse update(Long id, TimeslotRequest request);
    TimeslotResponse findById(Long id);
    List<TimeslotResponse> findAll();
    void delete(Long id);
}
