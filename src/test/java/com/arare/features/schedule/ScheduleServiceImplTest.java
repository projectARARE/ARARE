package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import com.arare.exception.ResourceBusyException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.solvejob.SolveJobResponse;
import com.arare.features.solvejob.SolveJobService;
import com.arare.features.solvejob.SolveJobStatus;
import com.arare.features.solvejob.SolveJobType;
import com.arare.features.solver.TimetableSolverService;
import com.arare.features.timeslot.TimeslotRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock private ScheduleRepository repo;
    @Mock private SolveJobService solveJobService;
    @Mock private ClassSessionRepository sessionRepo;
    @Mock private FeasibilityCheckService feasibilityCheckService;
    @Mock private TimeslotRepository timeslotRepo;
    @Mock private CascadeDeletionService cascadeDeletionService;
    @Mock private TimetableSolverService solverService;

    @InjectMocks
    private ScheduleServiceImpl service;

    private Schedule existingSchedule(Long id) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        return schedule;
    }

    @Test
    void delete_rejectsWhenSolveJobIsInProgress() {
        when(repo.findById(5L)).thenReturn(Optional.of(existingSchedule(5L)));
        doThrow(new ResourceBusyException("Schedule 5 has 1 solve job(s) in progress"))
            .when(solveJobService).ensureNoActiveJobForSchedule(5L);

        assertThrows(ResourceBusyException.class, () -> service.delete(5L));
        verify(cascadeDeletionService, never()).purgeScheduleTree(anyLong());
    }

    @Test
    void delete_cascadesWhenNoSolveJobIsInProgress() {
        when(repo.findById(5L)).thenReturn(Optional.of(existingSchedule(5L)));

        service.delete(5L);

        verify(solveJobService).ensureNoActiveJobForSchedule(5L);
        verify(cascadeDeletionService).purgeScheduleTree(5L);
    }

    @Test
    void delete_throwsWhenScheduleNotFound() {
        when(repo.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(cascadeDeletionService, never()).purgeScheduleTree(anyLong());
    }

    // -- generate(): feasibility gate + enqueue, no solver work in the request --

    @Test
    void generate_feasible_persistsDraftAndSubmitsSolveJob() {
        ScheduleRequest req = new ScheduleRequest("Term 1", ScheduleScope.DEPARTMENT,
            null, null, null, null, null, 30);
        when(feasibilityCheckService.check(req))
            .thenReturn(new FeasibilityCheckResult(true, 0, 0, 12, 40, List.of()));
        when(repo.save(any())).thenAnswer(inv -> {
            Schedule saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        SolveJobResponse job = new SolveJobResponse(1L, SolveJobType.GENERATE, 10L, SolveJobStatus.QUEUED,
            null, null, null, null, null, null, null);
        when(solveJobService.submitGenerate(10L, req)).thenReturn(job);

        SolveJobResponse response = service.generate(req);

        assertEquals(job, response);
        verify(repo).save(any());
        verify(solveJobService).submitGenerate(10L, req);
    }

    @Test
    void generate_infeasible_throwsBeforeSavingAnySchedule() {
        ScheduleRequest req = new ScheduleRequest("Term 1", ScheduleScope.DEPARTMENT,
            null, null, null, null, null, 30);
        when(feasibilityCheckService.check(req)).thenReturn(new FeasibilityCheckResult(false, 2, 0, 12, 8,
            List.of(new FeasibilityIssue(FeasibilityIssue.Severity.ERROR, "BATCH",
                "No batches found for the selected scope.", null, null))));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generate(req));

        assertEquals(true, ex.getMessage().contains("infeasible"));
        verify(repo, never()).save(any());
        verify(solveJobService, never()).submitGenerate(anyLong(), any());
    }

    @Test
    void generate_throwsWhenParentScheduleDoesNotExist() {
        ScheduleRequest req = new ScheduleRequest("Term 1", ScheduleScope.DEPARTMENT,
            99L, null, null, null, null, 30);
        when(feasibilityCheckService.check(req))
            .thenReturn(new FeasibilityCheckResult(true, 0, 0, 12, 40, List.of()));
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.generate(req));
        verify(solveJobService, never()).submitGenerate(anyLong(), any());
    }

    // -- partialResolve(): validates the schedule, then enqueues a re-solve --

    @Test
    void partialResolve_validatesScheduleThenSubmits() {
        when(repo.findById(5L)).thenReturn(Optional.of(existingSchedule(5L)));
        SolveJobResponse job = new SolveJobResponse(2L, SolveJobType.PARTIAL_RESOLVE, 5L, SolveJobStatus.QUEUED,
            null, null, null, null, null, null, null);
        when(solveJobService.submitPartialResolve(5L, List.of(11L, 12L))).thenReturn(job);

        SolveJobResponse response = service.partialResolve(5L, List.of(11L, 12L));

        assertEquals(job, response);
        verify(solveJobService).submitPartialResolve(5L, List.of(11L, 12L));
    }

    @Test
    void partialResolve_throwsWhenScheduleMissing() {
        when(repo.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.partialResolve(9L, List.of(11L)));

        verify(solveJobService, never()).submitPartialResolve(anyLong(), anyList());
    }

    // -- getExplanation(): stored text, or a deterministic default --

    @Test
    void getExplanation_returnsStoredTextWhenPresent() {
        Schedule schedule = existingSchedule(5L);
        schedule.setScoreExplanation("2hard/0medium/0soft");
        when(repo.findById(5L)).thenReturn(Optional.of(schedule));

        assertEquals("2hard/0medium/0soft", service.getExplanation(5L));
    }

    @Test
    void getExplanation_returnsDefaultWhenAbsent() {
        when(repo.findById(5L)).thenReturn(Optional.of(existingSchedule(5L)));

        assertEquals("No explanation available.", service.getExplanation(5L));
    }
}