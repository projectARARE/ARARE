package com.arare.features.cascadedeletion;

import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.event.EventRepository;
import com.arare.features.preallocation.PreAllocationRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CascadeDeletionService {

    private final PreAllocationRepository preAllocationRepo;
    private final ClassSessionRepository sessionRepo;
    private final ScheduleRepository scheduleRepo;
    private final TeacherRepository teacherRepo;
    private final RoomRepository roomRepo;
    private final TimeslotRepository timeslotRepo;
    private final EventRepository eventRepo;

    // Purges sessions, pre-allocations and schedule rows for the given schedule
    // and every descendant schedule (child schedules created from a parent).
    @Transactional
    public void purgeScheduleTree(Long scheduleId) {
        List<Long> ids = collectScheduleSubtree(scheduleId);
        for (Long id : ids) {
            sessionRepo.deleteByScheduleId(id);
            preAllocationRepo.deleteByScheduleId(id);
        }
        scheduleRepo.deleteAllByIdInBatch(ids);
    }

    @Transactional
    public void purgePreAllocationsForBatch(Long batchId) {
        preAllocationRepo.deleteByBatchId(batchId);
    }

    @Transactional
    public void purgePreAllocationsForSubject(Long subjectId) {
        preAllocationRepo.deleteBySubjectId(subjectId);
    }

    @Transactional
    public void purgePreAllocationsForTeacher(Long teacherId) {
        preAllocationRepo.deleteByTeacherId(teacherId);
    }

    @Transactional
    public void purgePreAllocationsForRoom(Long roomId) {
        preAllocationRepo.deleteByRoomId(roomId);
    }

    @Transactional
    public void purgePreAllocationsForTimeslot(Long timeslotId) {
        preAllocationRepo.deleteByTimeslotId(timeslotId);
    }

    @Transactional
    public void purgePreAllocationsForDepartment(Long departmentId) {
        preAllocationRepo.deleteByDepartmentId(departmentId);
    }

    // Removes a timeslot from teacher/room availability and event join tables
    @Transactional
    public void detachTimeslot(Long timeslotId) {
        teacherRepo.deleteTeacherAvailabilityByTimeslotId(timeslotId);
        roomRepo.deleteRoomAvailabilityByTimeslotId(timeslotId);
        eventRepo.deleteEventAffectedTimeslotsByTimeslotId(timeslotId);
    }

    @Transactional
    public void detachRoomFromEvents(Long roomId) {
        eventRepo.deleteEventAffectedRoomsByRoomId(roomId);
    }

    @Transactional
    public void detachTeacherFromEvents(Long teacherId) {
        eventRepo.deleteEventAffectedTeachersByTeacherId(teacherId);
    }

    private List<Long> collectScheduleSubtree(Long rootId) {
        List<Long> result = new ArrayList<>();
        var children = scheduleRepo.findByParentScheduleId(rootId);
        result.add(rootId);
        children.forEach(child -> {
            result.addAll(collectScheduleSubtree(child.getId()));
        });
        return result;
    }
}