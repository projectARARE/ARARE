package com.arare.features.solver;

import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.building.BuildingRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.preallocation.PreAllocation;
import com.arare.features.preallocation.PreAllocationRepository;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.teacherassignment.TeacherAssignment;
import com.arare.features.teacherassignment.TeacherAssignmentRepository;
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.universityconfig.UniversityConfigRepository;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaProblemDataGateway implements ProblemDataGateway {

    private final TimeslotRepository timeslotRepo;
    private final BuildingRepository buildingRepo;
    private final UniversityConfigRepository configRepo;
    private final RoomRepository roomRepo;
    private final TeacherRepository teacherRepo;
    private final SubjectRepository subjectRepo;
    private final BatchRepository batchRepo;
    private final ClassSectionRepository sectionRepo;
    private final ClassSessionRepository sessionRepo;
    private final PreAllocationRepository preAllocationRepo;
    private final TeacherAssignmentRepository assignmentRepo;

    @Override
    public ProblemFacts loadFacts(ProblemBuildRequest request) {
        List<Room> rooms = (request.roomIds() != null && !request.roomIds().isEmpty())
            ? resolveAll(roomRepo, request.roomIds(), "Room")
            : roomRepo.findAll();

        List<Teacher> teachers = (request.teacherIds() != null && !request.teacherIds().isEmpty())
            ? resolveAll(teacherRepo, request.teacherIds(), "Teacher")
            : teacherRepo.findAll();

        List<Subject> scoped = request.departmentId() != null
            ? subjectRepo.findByDepartmentId(request.departmentId())
            : request.instituteId() != null
                ? subjectRepo.findByDepartmentInstituteId(request.instituteId())
                : subjectRepo.findAll();
        // Institute-wide subjects (no owning department) are eligible in every
        // scope: they can be offered to any batch via SubjectOffering.
        List<Subject> subjects = new ArrayList<>(scoped);
        subjectRepo.findInstituteWide().forEach(instWide -> {
            if (subjects.stream().noneMatch(s -> s.getId().equals(instWide.getId()))) {
                subjects.add(instWide);
            }
        });

        List<Batch> batches = request.departmentId() != null
            ? batchRepo.findByDepartmentId(request.departmentId())
            : request.instituteId() != null
                ? batchRepo.findByDepartmentInstituteId(request.instituteId())
                : batchRepo.findAll();

        if (request.batchIds() != null && !request.batchIds().isEmpty()) {
            resolveAll(batchRepo, request.batchIds(), "Batch");
            batches = batches.stream()
                .filter(b -> request.batchIds().contains(b.getId()))
                .toList();
        }

        List<Long> batchIds = batches.stream().map(Batch::getId).toList();
        List<ClassSection> sections = !batchIds.isEmpty()
            ? sectionRepo.findByBatchIdIn(batchIds)
            : sectionRepo.findAll();

        return new ProblemFacts(
            timeslotRepo.findByType(TimeslotType.CLASS),
            buildingRepo.findAll(),
            configRepo.findFirstByActiveTrue().map(List::of).orElse(List.of()),
            rooms,
            teachers,
            subjects,
            batches,
            sections
        );
    }

    // Resolves every requested ID, rejecting unknown ones instead of silently
    // building a problem from a partial set.
    private <T> List<T> resolveAll(JpaRepository<T, Long> repo, List<Long> ids, String type) {
        List<T> found = repo.findAllById(ids);
        if (found.size() != new HashSet<>(ids).size()) {
            throw new IllegalArgumentException("One or more " + type + " ids do not exist: " + ids);
        }
        return found;
    }

    @Override
    public List<TeacherAssignment> loadAssignments(List<Long> batchIds, List<Long> sectionIds) {
        List<TeacherAssignment> assignments = new ArrayList<>();
        if (batchIds != null && !batchIds.isEmpty()) {
            assignments.addAll(assignmentRepo.findByBatchIdIn(batchIds));
        }
        if (sectionIds != null && !sectionIds.isEmpty()) {
            assignments.addAll(assignmentRepo.findBySectionIdIn(sectionIds));
        }
        return assignments;
    }

    @Override
    public List<ClassSession> findSessionsByScheduleId(Long scheduleId) {
        return sessionRepo.findByScheduleId(scheduleId);
    }

    @Override
    public List<ClassSession> saveSessions(List<ClassSession> sessions) {
        return sessionRepo.saveAll(sessions);
    }

    @Override
    public List<ClassSession> findLockedParentSessions(Long parentScheduleId) {
        return sessionRepo.findByScheduleId(parentScheduleId).stream()
            .filter(ClassSession::isLocked)
            .sorted(Comparator.comparing(ClassSession::getId))
            .toList();
    }

    @Override
    public List<PreAllocation> findLockedPreAllocations(Long scheduleId) {
        return preAllocationRepo.findByScheduleIdAndLocked(scheduleId, true);
    }

    @Override
    public List<TeacherBusyInterval> findTeacherBusyIntervals(Long scheduleId, Long instituteId, List<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            return List.of();
        }
        List<TeacherBusyInterval> busy = new ArrayList<>();
        for (Object[] row : sessionRepo.findActiveCrossScheduleBusyIntervals(scheduleId, teacherIds, instituteId)) {
            Number durationNum = (Number) row[5];
            Integer startSlot = (Integer) row[2];
            Integer endExclusive = startSlot != null ? startSlot + durationNum.intValue() : null;
            busy.add(new TeacherBusyInterval(
                (Long) row[0],
                (SchoolDay) row[1],
                startSlot,
                endExclusive,
                (LocalTime) row[3],
                (LocalTime) row[4]));
        }
        return busy;
    }

    @Override
    public List<PreviousAssignment> findPreviousAssignments(Long parentScheduleId) {
        return sessionRepo.findByScheduleId(parentScheduleId).stream()
            .filter(cs -> cs.getSubject() != null
                && cs.getTeacher() != null && cs.getRoom() != null && cs.getTimeslot() != null)
            .map(cs -> new PreviousAssignment(
                PreviousAssignment.keyFor(cs),
                cs.getTeacher().getId(),
                cs.getRoom().getId(),
                cs.getTimeslot().getId()))
            .toList();
    }
}
