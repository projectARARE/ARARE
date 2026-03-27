package com.arare.features.solver;

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
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.universityconfig.UniversityConfigRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    @Override
    public ProblemFacts loadFacts(ProblemBuildRequest request) {
        List<Room> rooms = (request.roomIds() != null && !request.roomIds().isEmpty())
            ? roomRepo.findAllById(request.roomIds())
            : roomRepo.findAll();

        List<Teacher> teachers = (request.teacherIds() != null && !request.teacherIds().isEmpty())
            ? teacherRepo.findAllById(request.teacherIds())
            : teacherRepo.findAll();

        List<Subject> subjects = request.departmentId() != null
            ? subjectRepo.findByDepartmentId(request.departmentId())
            : subjectRepo.findAll();

        List<Batch> batches = request.departmentId() != null
            ? batchRepo.findByDepartmentId(request.departmentId())
            : batchRepo.findAll();

        if (request.batchIds() != null && !request.batchIds().isEmpty()) {
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
            configRepo.findByActiveTrue().map(List::of).orElse(List.of()),
            rooms,
            teachers,
            subjects,
            batches,
            sections
        );
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
            .sorted(java.util.Comparator.comparing(ClassSession::getId))
            .toList();
    }

    @Override
    public List<PreAllocation> findLockedPreAllocations(Long scheduleId) {
        return preAllocationRepo.findByScheduleIdAndLocked(scheduleId, true);
    }
}
