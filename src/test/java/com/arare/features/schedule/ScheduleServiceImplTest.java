package com.arare.features.schedule;

import com.arare.common.enums.ScheduleScope;
import com.arare.exception.InfeasibleScheduleException;
import com.arare.exception.ResourceBusyException;
import com.arare.exception.ResourceNotFoundException;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.cascadedeletion.CascadeDeletionService;
import com.arare.features.solvejob.SolveJobResponse;
import com.arare.features.solvejob.SolveJobService;
import com.arare.features.solvejob.SolveJobStatus;
import com.arare.features.solvejob.SolveJobType;
import com.arare.features.solver.ScoreExplanationResponse;
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

    private void stubSaveReturnsArgument() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
            null, null, null, null, null, null, 30, null, null);
        when(feasibilityCheckService.check(req))
            .thenReturn(new FeasibilityCheckResult(true, 0, 0, 12, 40, 0, 0, 1800, List.of()));
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
            null, null, null, null, null, null, 30, null, null);
        when(feasibilityCheckService.check(req)).thenReturn(new FeasibilityCheckResult(false, 2, 0, 12, 8,
            0, 0, 1800,
            List.of(new FeasibilityIssue(FeasibilityIssue.Severity.ERROR, "BATCH",
                "No batches found for the selected scope.", null, null))));

        InfeasibleScheduleException ex = assertThrows(InfeasibleScheduleException.class, () -> service.generate(req));

        assertEquals(true, ex.getMessage().contains("infeasible"));
        verify(repo, never()).save(any());
        verify(solveJobService, never()).submitGenerate(anyLong(), any());
    }

    @Test
    void generate_throwsWhenParentScheduleDoesNotExist() {
        ScheduleRequest req = new ScheduleRequest("Term 1", ScheduleScope.DEPARTMENT,
            99L, null, null, null, null, null, 30, null, null);
        when(feasibilityCheckService.check(req))
            .thenReturn(new FeasibilityCheckResult(true, 0, 0, 12, 40, 0, 0, 1800, List.of()));
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

    // -- activate(): publishes a solved draft, superseding other actives --

    @Test
    void activate_publishesSolvedDraftAndArchivesOtherActiveSameInstitute() {
        Schedule draft = existingSchedule(5L);
        draft.setStatus(com.arare.common.enums.ScheduleStatus.DRAFT);
        draft.setScore("0hard/0medium/2soft");

        Schedule otherActive = existingSchedule(4L);
        otherActive.setStatus(com.arare.common.enums.ScheduleStatus.ACTIVE);
        otherActive.setInstituteId(null);

        when(repo.findById(5L)).thenReturn(Optional.of(draft));
        when(repo.findByStatus(com.arare.common.enums.ScheduleStatus.ACTIVE))
            .thenReturn(List.of(otherActive));
        stubSaveReturnsArgument();

        var response = service.activate(5L);

        assertEquals(com.arare.common.enums.ScheduleStatus.ACTIVE, response.status());
        assertEquals(com.arare.common.enums.ScheduleStatus.ARCHIVED, otherActive.getStatus());
        verify(repo).save(otherActive);
        verify(repo).save(draft);
    }

    @Test
    void activate_keepsActiveOfDifferentInstitute() {
        Schedule draft = existingSchedule(5L);
        draft.setStatus(com.arare.common.enums.ScheduleStatus.DRAFT);
        draft.setScore("0hard/0medium/2soft");
        draft.setInstituteId(2L);

        Schedule otherActive = existingSchedule(4L);
        otherActive.setStatus(com.arare.common.enums.ScheduleStatus.ACTIVE);
        otherActive.setInstituteId(1L);

        when(repo.findById(5L)).thenReturn(Optional.of(draft));
        when(repo.findByStatus(com.arare.common.enums.ScheduleStatus.ACTIVE))
            .thenReturn(List.of(otherActive));
        stubSaveReturnsArgument();

        service.activate(5L);

        assertEquals(com.arare.common.enums.ScheduleStatus.ACTIVE, otherActive.getStatus());
        verify(repo, never()).save(otherActive);
    }

    @Test
    void activate_throwsWhenScheduleHasNoSolution() {
        Schedule draft = existingSchedule(5L);
        draft.setStatus(com.arare.common.enums.ScheduleStatus.DRAFT);
        draft.setScore(null);
        when(repo.findById(5L)).thenReturn(Optional.of(draft));

        assertThrows(com.arare.exception.ResourceConflictException.class, () -> service.activate(5L));
        verify(repo, never()).save(any());
    }

    @Test
    void activate_throwsWhenScheduleArchived() {
        Schedule archived = existingSchedule(5L);
        archived.setStatus(com.arare.common.enums.ScheduleStatus.ARCHIVED);
        when(repo.findById(5L)).thenReturn(Optional.of(archived));

        assertThrows(com.arare.exception.ResourceConflictException.class, () -> service.activate(5L));
    }

    @Test
    void activate_throwsWhenSolveJobInProgress() {
        Schedule draft = existingSchedule(5L);
        draft.setStatus(com.arare.common.enums.ScheduleStatus.DRAFT);
        draft.setScore("0hard/0medium/2soft");
        when(repo.findById(5L)).thenReturn(Optional.of(draft));
        doThrow(new ResourceBusyException("Schedule 5 has 1 solve job(s) in progress"))
            .when(solveJobService).ensureNoActiveJobForSchedule(5L);

        assertThrows(ResourceBusyException.class, () -> service.activate(5L));
        verify(repo, never()).save(any());
    }

    // -- archive(): moves any schedule to ARCHIVED --

    @Test
    void archive_setsArchivedStatus() {
        Schedule active = existingSchedule(5L);
        active.setStatus(com.arare.common.enums.ScheduleStatus.ACTIVE);
        when(repo.findById(5L)).thenReturn(Optional.of(active));
        stubSaveReturnsArgument();

        var response = service.archive(5L);

        assertEquals(com.arare.common.enums.ScheduleStatus.ARCHIVED, response.status());
        verify(repo).save(active);
    }

    // -- revalidate(): re-scores current sessions; INFEASIBLE -> DRAFT when hard = 0 --

    @Test
    void revalidate_infeasibleFlipsToDraftWhenHardCleared() {
        Schedule infeasible = existingSchedule(5L);
        infeasible.setStatus(com.arare.common.enums.ScheduleStatus.INFEASIBLE);
        infeasible.setScore("-2hard/0medium/0soft");
        when(repo.findById(5L)).thenReturn(Optional.of(infeasible));
        when(solverService.explainSchedule(5L))
            .thenReturn(new ScoreExplanationResponse("0hard/-10medium/-5soft", true, 0, -10, -5, List.of()));
        stubSaveReturnsArgument();

        var response = service.revalidate(5L);

        assertEquals(com.arare.common.enums.ScheduleStatus.DRAFT, response.status());
        assertEquals("0hard/-10medium/-5soft", response.score());
        verify(repo).save(infeasible);
    }

    @Test
    void revalidate_feasibleDraftStaysDraftAndRefreshesScore() {
        Schedule draft = existingSchedule(5L);
        draft.setStatus(com.arare.common.enums.ScheduleStatus.DRAFT);
        draft.setScore("0hard/0medium/0soft");
        when(repo.findById(5L)).thenReturn(Optional.of(draft));
        when(solverService.explainSchedule(5L))
            .thenReturn(new ScoreExplanationResponse("0hard/-688medium/-882soft", true, 0, -688, -882, List.of()));
        stubSaveReturnsArgument();

        var response = service.revalidate(5L);

        assertEquals(com.arare.common.enums.ScheduleStatus.DRAFT, response.status());
        assertEquals("0hard/-688medium/-882soft", response.score());
    }

    @Test
    void revalidate_makesDraftInfeasibleWhenHardReturns() {
        Schedule draft = existingSchedule(5L);
        draft.setStatus(com.arare.common.enums.ScheduleStatus.DRAFT);
        when(repo.findById(5L)).thenReturn(Optional.of(draft));
        when(solverService.explainSchedule(5L))
            .thenReturn(new ScoreExplanationResponse("-1hard/-3medium/-1soft", false, -1, -3, -1, List.of()));
        stubSaveReturnsArgument();

        var response = service.revalidate(5L);

        assertEquals(com.arare.common.enums.ScheduleStatus.INFEASIBLE, response.status());
    }

    @Test
    void revalidate_withoutSolution_throws() {
        Schedule draft = existingSchedule(5L);
        draft.setStatus(com.arare.common.enums.ScheduleStatus.INFEASIBLE);
        when(repo.findById(5L)).thenReturn(Optional.of(draft));
        when(solverService.explainSchedule(5L))
            .thenReturn(new ScoreExplanationResponse("N/A", false, 0, 0, 0, List.of()));

        assertThrows(com.arare.exception.ResourceConflictException.class, () -> service.revalidate(5L));
        verify(repo, never()).save(any());
    }

    @Test
    void revalidate_active_throws() {
        Schedule active = existingSchedule(5L);
        active.setStatus(com.arare.common.enums.ScheduleStatus.ACTIVE);
        when(repo.findById(5L)).thenReturn(Optional.of(active));

        assertThrows(com.arare.exception.ResourceConflictException.class, () -> service.revalidate(5L));
        verify(solverService, never()).explainSchedule(anyLong());
    }

    @Test
    void revalidate_archivedRestoresToDraftWhenFeasible() {
        Schedule archived = existingSchedule(6L);
        archived.setStatus(com.arare.common.enums.ScheduleStatus.ARCHIVED);
        archived.setScore("0hard/-685medium/-833soft");
        when(repo.findById(6L)).thenReturn(Optional.of(archived));
        when(solverService.explainSchedule(6L))
            .thenReturn(new ScoreExplanationResponse("0hard/-685medium/-833soft", true, 0, -685, -833, List.of()));
        stubSaveReturnsArgument();

        var response = service.revalidate(6L);

        assertEquals(com.arare.common.enums.ScheduleStatus.DRAFT, response.status());
        assertEquals("0hard/-685medium/-833soft", response.score());
        verify(repo).save(archived);
    }

    @Test
    void revalidate_rejectsWhenSolveJobInProgress() {
        Schedule infeasible = existingSchedule(5L);
        infeasible.setStatus(com.arare.common.enums.ScheduleStatus.INFEASIBLE);
        when(repo.findById(5L)).thenReturn(Optional.of(infeasible));
        doThrow(new ResourceBusyException("Schedule 5 has 1 solve job(s) in progress"))
            .when(solveJobService).ensureNoActiveJobForSchedule(5L);

        assertThrows(ResourceBusyException.class, () -> service.revalidate(5L));
        verify(repo, never()).save(any());
    }
}