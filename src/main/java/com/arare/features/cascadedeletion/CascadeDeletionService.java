package com.arare.features.cascadedeletion;

import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.preallocation.PreAllocationRepository;
import com.arare.features.schedule.ScheduleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// Centralised child-row cleanup that must run before a parent row is deleted.
// <p>pre_allocations holds non-null foreign keys to schedule, batch, subject and
// timeslot (and nullable keys to teacher and room), so those rows must be purged
// first or the delete would violate a foreign-key constraint. Schedules also
// reference a parent schedule, so deleting one requires descending the child
// tree first.</p>
@Service
@RequiredArgsConstructor
public class CascadeDeletionService {

    private final PreAllocationRepository preAllocationRepo;
    private final ClassSessionRepository sessionRepo;
    private final ScheduleRepository scheduleRepo;
    private final EntityManager entityManager;

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
    // (all owned by the other side, so Hibernate will not clean them up).
    @Transactional
    public void detachTimeslot(Long timeslotId) {
        entityManager.createNativeQuery("DELETE FROM teacher_availability WHERE timeslot_id = :id")
            .setParameter("id", timeslotId)
            .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM room_availability WHERE timeslot_id = :id")
            .setParameter("id", timeslotId)
            .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM event_affected_timeslots WHERE timeslot_id = :id")
            .setParameter("id", timeslotId)
            .executeUpdate();
    }

    @Transactional
    public void detachRoomFromEvents(Long roomId) {
        entityManager.createNativeQuery("DELETE FROM event_affected_rooms WHERE room_id = :id")
            .setParameter("id", roomId)
            .executeUpdate();
    }

    @Transactional
    public void detachTeacherFromEvents(Long teacherId) {
        entityManager.createNativeQuery("DELETE FROM event_affected_teachers WHERE teacher_id = :id")
            .setParameter("id", teacherId)
            .executeUpdate();
    }

    private List<Long> collectScheduleSubtree(Long rootId) {
        List<Long> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);
            scheduleRepo.findByParentScheduleId(current)
                .forEach(child -> queue.add(child.getId()));
        }
        return result;
    }
}
