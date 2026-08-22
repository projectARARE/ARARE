package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.arare.common.enums.ScheduleStatus;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolutionPersisterTest {

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private ClassSessionRepository sessionRepo;

    @Mock
    private SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;

    @InjectMocks
    private SolutionPersister persister;

    @Test
    void nullScoreThrowsIllegalStateExceptionWithoutNPE() {
        Schedule schedule = Schedule.builder().name("Test").build();
        schedule.setId(1L);

        TimetableSolution solution = new TimetableSolution();
        solution.setSessions(List.of());
        solution.setScore(null);

        assertThrows(
            IllegalStateException.class,
            () -> persister.persist(schedule, solution)
        );
    }

    @Test
    void infeasibleHardScorePersistsPartialAndMarksScheduleInfeasible() {
        Schedule schedule = Schedule.builder().name("Test").build();
        schedule.setId(1L);

        TimetableSolution solution = new TimetableSolution();
        solution.setSessions(List.of());
        solution.setScore(HardMediumSoftScore.of(-1, 0, 0));

        ScoreExplanation<TimetableSolution, HardMediumSoftScore> explanation = mockExplanation();

        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(solutionManager.explain(any())).thenReturn(explanation);
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of());

        SolutionPersister.PersistResult result = persister.persist(schedule, solution);

        assertEquals(SolutionPersister.PersistResult.INFEASIBLE, result);
        assertEquals(ScheduleStatus.INFEASIBLE, schedule.getStatus());
        verify(scheduleRepo).save(any());
    }

    @Test
    void feasibleHardScorePersistsAndKeepsScheduleDraft() {
        Schedule schedule = Schedule.builder().name("Test").build();
        schedule.setId(1L);

        TimetableSolution solution = new TimetableSolution();
        solution.setSessions(List.of());
        solution.setScore(HardMediumSoftScore.of(0, 0, 0));

        ScoreExplanation<TimetableSolution, HardMediumSoftScore> explanation = mockExplanation();

        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(solutionManager.explain(any())).thenReturn(explanation);
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of());

        SolutionPersister.PersistResult result = persister.persist(schedule, solution);

        assertEquals(SolutionPersister.PersistResult.FEASIBLE, result);
        assertEquals(ScheduleStatus.DRAFT, schedule.getStatus());
        verify(scheduleRepo).save(any());
    }

    @Test
    void persistCopiesAssignmentsToManagedSessions() {
        Schedule schedule = Schedule.builder().name("Test").build();
        schedule.setId(1L);

        ClassSession managed = ClassSession.builder().build();
        managed.setId(5L);

        ClassSession solved = ClassSession.builder().build();
        solved.setId(5L);
        solved.setTeacher(null);
        solved.setRoom(null);
        solved.setTimeslot(null);
        solved.setLocked(true);

        TimetableSolution solution = new TimetableSolution();
        solution.setSessions(List.of(solved));
        solution.setScore(HardMediumSoftScore.of(0, 0, 0));

        ScoreExplanation<TimetableSolution, HardMediumSoftScore> explanation = mockExplanation();

        when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
        when(solutionManager.explain(any())).thenReturn(explanation);
        when(sessionRepo.findByScheduleId(1L)).thenReturn(List.of(managed));

        persister.persist(schedule, solution);

        assertEquals(true, managed.isLocked());
        verify(sessionRepo).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private ScoreExplanation<TimetableSolution, HardMediumSoftScore> mockExplanation() {
        ScoreExplanation<TimetableSolution, HardMediumSoftScore> explanation =
            org.mockito.Mockito.mock(ScoreExplanation.class);
        org.mockito.Mockito.when(explanation.toString()).thenReturn("explanation");
        return explanation;
    }
}