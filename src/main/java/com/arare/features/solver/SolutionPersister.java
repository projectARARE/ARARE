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

/**
 * Writes a solver result back to the database.
 *
 * <p>An infeasible result (negative hard score) is <em>still persisted</em>:
 * the best-effort placement is kept so operators can inspect which sessions
 * conflict and why, and the schedule is marked {@link ScheduleStatus#INFEASIBLE}
 * (read-only, not activatable). Only a truly unsolved solution (null score)
 * aborts with an exception.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionPersister {

    public enum PersistResult {
        FEASIBLE,
        INFEASIBLE
    }

    private final ScheduleRepository scheduleRepo;
    private final ClassSessionRepository sessionRepo;
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;

    @Transactional
    public PersistResult persist(Schedule schedule, TimetableSolution solution) {
        HardMediumSoftScore score = solution.getScore();

        if (score == null) {
            throw new IllegalStateException(
                "Generated schedule is unsolved. Please try again.");
        }
        boolean feasible = score.hardScore() >= 0;

        Schedule managedSchedule = scheduleRepo.findById(schedule.getId())
            .orElseThrow(() -> new IllegalStateException(
                "Schedule not found: " + schedule.getId()));
        managedSchedule.setScore(score.toString());
        managedSchedule.setScoreExplanation(solutionManager.explain(solution).toString());
        managedSchedule.setStatus(feasible ? ScheduleStatus.DRAFT : ScheduleStatus.INFEASIBLE);
        scheduleRepo.save(managedSchedule);

        Map<Long, ClassSession> solvedById = solution.getSessions().stream()
            .filter(s -> s.getId() != null)
            .collect(Collectors.toMap(ClassSession::getId, s -> s));

        if (log.isDebugEnabled()) {
            solution.getSessions().forEach(s -> log.debug(
                "SOLVED session id={} teacher={} room={} timeslot={}",
                s.getId(),
                s.getTeacher() != null ? s.getTeacher().getId() : "NULL",
                s.getRoom() != null ? s.getRoom().getId() : "NULL",
                s.getTimeslot() != null ? s.getTimeslot().getId() : "NULL"
            ));
        }
        long unassigned = solution.getSessions().stream().filter(s -> s.getTimeslot() == null).count();
        log.info("Schedule [{}] solved. Score: {} ({} sessions, {} unassigned){}",
            schedule.getId(), score, solution.getSessions().size(), unassigned,
            feasible ? "" : " — INFEASIBLE (partial result persisted)");

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

        return feasible ? PersistResult.FEASIBLE : PersistResult.INFEASIBLE;
    }
}