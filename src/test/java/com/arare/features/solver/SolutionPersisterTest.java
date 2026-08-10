package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void negativeHardScoreThrowsIllegalStateExceptionWithoutNPE() {
        Schedule schedule = Schedule.builder().name("Test").build();
        schedule.setId(1L);

        TimetableSolution solution = new TimetableSolution();
        solution.setSessions(List.of());
        solution.setScore(HardMediumSoftScore.of(-1, 0, 0));

        assertThrows(
            IllegalStateException.class,
            () -> persister.persist(schedule, solution)
        );
    }
}
