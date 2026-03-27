package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.arare.common.enums.ScheduleStatus;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionPersister {

    private final ScheduleRepository scheduleRepo;
    private final ClassSessionRepository sessionRepo;
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;

    @Transactional
    public void persist(Schedule schedule, TimetableSolution solution) {
        HardMediumSoftScore score = solution.getScore();

        if (score == null || score.hardScore() < 0) {
            String scoreText = score != null ? score.toString() : "N/A";
            throw new IllegalStateException(
                "Generated schedule is infeasible (Hard Score: " + score.hardScore() 
                + "). Please check for conflicting pre-allocations or extreme resource shortages.");
        }

        schedule.setScore(score.toString());
        schedule.setScoreExplanation(solutionManager.explain(solution).toString());
        schedule.setStatus(determineStatus(score));
        scheduleRepo.save(schedule);

        Map<Long, ClassSession> solvedById = solution.getSessions().stream()
            .filter(s -> s.getId() != null)
            .collect(Collectors.toMap(ClassSession::getId, s -> s));

        solution.getSessions().forEach(s -> log.info(
            "SOLVED session id={} teacher={} room={} timeslot={}",
            s.getId(),
            s.getTeacher() != null ? s.getTeacher().getId() : "NULL",
            s.getRoom() != null ? s.getRoom().getId() : "NULL",
            s.getTimeslot() != null ? s.getTimeslot().getId() : "NULL"
        ));

        List<ClassSession> managed = sessionRepo.findByScheduleId(schedule.getId());
        for (ClassSession managedSession : managed) {
            ClassSession solved = solvedById.get(managedSession.getId());
            if (solved != null) {
                managedSession.setTeacher(solved.getTeacher());
                managedSession.setRoom(solved.getRoom());
                managedSession.setTimeslot(solved.getTimeslot());
                managedSession.setLocked(solved.isLocked());
            }
        }
        sessionRepo.saveAll(managed);

        log.info("Schedule [{}] solved. Score: {}", schedule.getId(), score);
    }

    private ScheduleStatus determineStatus(HardMediumSoftScore score) {
        if (score == null) {
            return ScheduleStatus.INFEASIBLE;
        }
        if (score.hardScore() < 0) {
            return ScheduleStatus.INFEASIBLE;
        }
        return ScheduleStatus.ACTIVE;
    }
}
