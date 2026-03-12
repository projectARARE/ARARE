package com.arare.features.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverJobBuilder;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.arare.common.enums.ScheduleStatus;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.batch.BatchRepository;
import com.arare.features.building.Building;
import com.arare.features.building.BuildingRepository;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsection.ClassSectionRepository;
import com.arare.features.classsession.ClassSession;
import com.arare.features.classsession.ClassSessionRepository;
import com.arare.features.preallocation.PreAllocation;
import com.arare.features.preallocation.PreAllocationRepository;
import com.arare.features.room.Room;
import com.arare.features.room.RoomRepository;
import com.arare.features.schedule.Schedule;
import com.arare.features.schedule.ScheduleRepository;
import com.arare.features.subject.Subject;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.Teacher;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.timeslot.TimeslotRepository;
import com.arare.features.universityconfig.UniversityConfig;
import com.arare.features.universityconfig.UniversityConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Orchestrates the full solve lifecycle:
 *
 * <pre>
 *  1. Load all problem facts from the database.
 *  2. Generate ClassSession planning entities (one per subject-chunk per batch/section).
 *  3. Apply PreAllocations (locked assignments).
 *  4. Build TimetableSolution and submit to Timefold SolverManager.
 *  5. Persist the solved solution back to the database.
 * </pre>
 *
 * <p>Partial re-optimization reuses session records from the parent schedule
 * and only recreates sessions impacted by the disruption.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableSolverService {

    private final SolverManager<TimetableSolution, UUID> solverManager;
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;

    private final ScheduleRepository       scheduleRepo;
    private final ClassSessionRepository   sessionRepo;
    private final TimeslotRepository       timeslotRepo;
    private final RoomRepository           roomRepo;
    private final TeacherRepository        teacherRepo;
    private final SubjectRepository        subjectRepo;
    private final BatchRepository          batchRepo;
    private final ClassSectionRepository   sectionRepo;
    private final BuildingRepository       buildingRepo;
    private final UniversityConfigRepository configRepo;
    private final PreAllocationRepository  preAllocationRepo;

    // ------------------------------------------------------------------
    // Full solve
    // ------------------------------------------------------------------

    /**
     * Generates a timetable from scratch for the given schedule record.
     *
     * @param scheduleId         ID of the Schedule entity that was pre-created by the caller.
     * @param departmentId       Optional – when provided, only batches/subjects for that department are scheduled.
     * @param batchIds           Optional – builder mode: limit to these batches only.
     * @param teacherIds         Optional – builder mode: limit to these teachers only.
     * @param roomIds            Optional – builder mode: limit to these rooms only.
     * @param solvingTimeSeconds Optional – per-request solve time limit in seconds (default: application config).
     */
    @Transactional
    public void solveSchedule(Long scheduleId, Long departmentId,
                              List<Long> batchIds, List<Long> teacherIds, List<Long> roomIds,
                              Integer solvingTimeSeconds) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        TimetableSolution problem = buildProblem(schedule, null, departmentId, batchIds, teacherIds, roomIds);
        TimetableSolution solution = runSolver(problem, solvingTimeSeconds);
        persistSolution(schedule, solution);
    }

    // ------------------------------------------------------------------
    // Partial re-optimization
    // ------------------------------------------------------------------

    /**
     * Re-solves only the sessions impacted by a disruption (teacher absent, room closed, etc.).
     *
     * <p>Impacted sessions are identified by the caller and passed as a list of IDs.
     * All other sessions are locked before the solver runs.</p>
     *
     * @param scheduleId        Active schedule to re-optimize.
     * @param impactedSessionIds Sessions that must be reassigned.
     */
    @Transactional
    public void partialResolve(Long scheduleId, List<Long> impactedSessionIds) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        TimetableSolution problem = buildProblem(schedule, impactedSessionIds, null, null, null, null);
        TimetableSolution solution = runSolver(problem, null);
        persistSolution(schedule, solution);
    }

    // ------------------------------------------------------------------
    // Score explanation
    // ------------------------------------------------------------------

    /**
     * Returns a human-readable breakdown of constraint violations for a schedule.
     *
     * <p>Uses Timefold's {@link SolutionManager} to calculate match totals
     * without running the solver again — it just evaluates the current state.</p>
     */
    @Transactional(readOnly = true)
    public ScoreExplanationResponse explainSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        TimetableSolution solution = buildProblem(schedule, null, null, null, null, null);
        var explanation = solutionManager.explain(solution);
        HardMediumSoftScore score = explanation.getScore();

        Collection<ConstraintMatchTotal<HardMediumSoftScore>> totals =
            explanation.getConstraintMatchTotalMap().values();

        List<ScoreExplanationResponse.ConstraintBreakdown> breakdowns = totals.stream()
            .filter(t -> !t.getScore().equals(HardMediumSoftScore.ZERO))
            .map(t -> {
                HardMediumSoftScore cs = t.getScore();
                String level = cs.hardScore() != 0 ? "HARD"
                    : cs.mediumScore() != 0 ? "MEDIUM" : "SOFT";
                return new ScoreExplanationResponse.ConstraintBreakdown(
                    t.getConstraintRef().constraintName(),
                    level,
                    t.getConstraintMatchCount(),
                    cs.toString()
                );
            })
            .sorted(java.util.Comparator.comparing(ScoreExplanationResponse.ConstraintBreakdown::level))
            .toList();

        return new ScoreExplanationResponse(
            score != null ? score.toString() : "N/A",
            score != null && score.isFeasible(),
            score != null ? score.hardScore() : 0,
            score != null ? score.mediumScore() : 0,
            score != null ? score.softScore() : 0,
            breakdowns
        );
    }

    // ------------------------------------------------------------------
    // Problem construction
    // ------------------------------------------------------------------

    /**
     * Assembles the {@link TimetableSolution} object from database state.
     *
     * @param schedule         Target schedule.
     * @param impactedIds      When non-null, all sessions NOT in this list are locked.
     * @param departmentId     When non-null, only batches / subjects for that department are loaded.
     * @param filterBatchIds   Builder mode: when non-empty, restrict to these batches only.
     * @param filterTeacherIds Builder mode: when non-empty, restrict to these teachers only.
     * @param filterRoomIds    Builder mode: when non-empty, restrict to these rooms only.
     */
    private TimetableSolution buildProblem(Schedule schedule, List<Long> impactedIds, Long departmentId,
                                           List<Long> filterBatchIds, List<Long> filterTeacherIds, List<Long> filterRoomIds) {
        // Problem facts
        List<Timeslot>       timeslots = timeslotRepo.findByType(TimeslotType.CLASS);
        List<Building>       buildings = buildingRepo.findAll();
        List<UniversityConfig> configs = configRepo.findAll();

        // Apply builder mode room/teacher filters
        List<Room> rooms = (filterRoomIds != null && !filterRoomIds.isEmpty())
            ? roomRepo.findAllById(filterRoomIds)
            : roomRepo.findAll();
        List<Teacher> teachers = (filterTeacherIds != null && !filterTeacherIds.isEmpty())
            ? teacherRepo.findAllById(filterTeacherIds)
            : teacherRepo.findAll();

        // Filter by department when generating a department-scoped schedule
        List<Subject> subjects = departmentId != null
            ? subjectRepo.findByDepartmentId(departmentId)
            : subjectRepo.findAll();
        List<Batch> batches = departmentId != null
            ? batchRepo.findByDepartmentId(departmentId)
            : batchRepo.findAll();

        // Apply builder mode batch filter (after department filter)
        if (filterBatchIds != null && !filterBatchIds.isEmpty()) {
            batches = batches.stream()
                .filter(b -> filterBatchIds.contains(b.getId()))
                .toList();
        }

        // Sections belonging to the resolved batches
        List<Long> batchIds = batches.stream().map(Batch::getId).toList();
        List<ClassSection> sections = !batchIds.isEmpty()
            ? sectionRepo.findByBatchIdIn(batchIds)
            : sectionRepo.findAll();

        // Generate or reuse ClassSession planning entities
        List<ClassSession> sessions = getOrGenerateSessions(schedule, subjects, batches, sections);

        // Apply pre-allocations (lock manual assignments)
        applyPreAllocations(sessions, schedule.getId());

        // For partial re-solve: lock everything not in impactedIds
        if (impactedIds != null) {
            sessions.forEach(s -> {
                if (!impactedIds.contains(s.getId())) {
                    s.setLocked(true);
                }
            });
        }

        // Force-initialize all Hibernate lazy associations before we hand the data
        // off to Timefold solver threads, which run outside this Hibernate session.
        initializeLazyAssociations(sessions, teachers, rooms, subjects, batches, sections);

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeslots(timeslots);
        problem.setRooms(rooms);
        problem.setTeachers(teachers);
        problem.setSubjects(subjects);
        problem.setBatches(batches);
        problem.setClassSections(sections);
        problem.setBuildings(buildings);
        problem.setConfigs(configs);
        problem.setSessions(sessions);
        return problem;
    }

    /**
     * Generates ClassSession entities for a fresh schedule or loads existing ones
     * from a derived (partial re-optimization) schedule.
     *
     * <p>Session generation formula:
     * <pre>
     *   sessionsNeeded = subject.weeklyHours / subject.chunkHours
     *   if isLab → one session per ClassSection
     *   else     → one session per Batch
     * </pre>
     * </p>
     */
    private List<ClassSession> getOrGenerateSessions(
        Schedule schedule,
        List<Subject> subjects,
        List<Batch> batches,
        List<ClassSection> sections
    ) {
        // If this is a re-optimization, load existing sessions
        List<ClassSession> existing = sessionRepo.findByScheduleId(schedule.getId());
        if (!existing.isEmpty()) return existing;

        List<ClassSession> generated = new ArrayList<>();

        for (Batch batch : batches) {
            for (Subject subject : subjects) {
                // Only schedule subjects belonging to the batch's department
                if (!subject.getDepartment().getId().equals(batch.getDepartment().getId())) {
                    continue;
                }

                int count = subject.getWeeklyHours() / subject.getChunkHours();

                if (subject.isLab()) {
                    // One session per lab section
                    List<ClassSection> batchSections = sections.stream()
                        .filter(s -> s.getBatch().getId().equals(batch.getId()))
                        .toList();

                    for (int i = 0; i < count; i++) {
                        for (ClassSection section : batchSections) {
                            generated.add(ClassSession.builder()
                                .subject(subject)
                                .batch(null)         // section replaces batch for labs
                                .section(section)
                                .schedule(schedule)
                                .duration(subject.getChunkHours())
                                .isLocked(false)
                                .build());
                        }
                    }
                } else {
                    // One session per batch per occurrence
                    for (int i = 0; i < count; i++) {
                        generated.add(ClassSession.builder()
                            .subject(subject)
                            .batch(batch)
                            .section(null)
                            .schedule(schedule)
                            .duration(subject.getChunkHours())
                            .isLocked(false)
                            .build());
                    }
                }
            }
        }

        return sessionRepo.saveAll(generated);
    }

    /**
     * Applies locked pre-allocations by setting teacher/room/timeslot on matching sessions
     * and marking them as {@code isLocked = true}.
     */
    private void applyPreAllocations(List<ClassSession> sessions, Long scheduleId) {
        List<PreAllocation> locked = preAllocationRepo.findByScheduleIdAndLocked(scheduleId, true);

        for (PreAllocation pa : locked) {
            sessions.stream()
                .filter(s -> !s.isLocked()
                    && s.getSubject().getId().equals(pa.getSubject().getId())
                    && s.getBatch() != null
                    && s.getBatch().getId().equals(pa.getBatch().getId()))
                .findFirst()
                .ifPresent(s -> {
                    s.setTeacher(pa.getTeacher());
                    s.setRoom(pa.getRoom());
                    s.setTimeslot(pa.getTimeslot());
                    s.setLocked(true);
                });
        }
    }

    // ------------------------------------------------------------------
    // Lazy-association initialisation
    // ------------------------------------------------------------------

    /**
     * Forces Hibernate to load every association that the Timefold constraint
     * streams will access during solving.  Must be called from the thread that
     * owns the current Hibernate session (i.e. inside the @Transactional boundary)
     * so that the data is already resident in memory before solver threads start.
     */
    private void initializeLazyAssociations(
        List<ClassSession> sessions,
        List<Teacher> teachers,
        List<Room> rooms,
        List<Subject> subjects,
        List<Batch> batches,
        List<ClassSection> sections
    ) {
        // --- Sessions ---
        for (ClassSession s : sessions) {
            // subject and its department+buildings (used in many constraints)
            Subject sub = s.getSubject();
            sub.getDepartment().getId();
            sub.getDepartment().getBuildingsAllowed().size();

            // batch (batchConflict, freeDayPreference, studentIdleGap, etc.)
            if (s.getBatch() != null) {
                Batch b = s.getBatch();
                b.getDepartment().getId();
                b.getWorkingDays().size();
            }
            // section (labSessionsMustUseSections)
            if (s.getSection() != null) {
                s.getSection().getId();
                s.getSection().getBatch().getId();
            }
        }

        // --- Teachers ---
        for (Teacher t : teachers) {
            t.getSubjects().size();           // teacherNotQualified
            t.getPreferredBuildings().size(); // preferTeacherBuilding
            t.getAvailableTimeslots().size(); // teacherUnavailable
        }

        // --- Rooms ---
        for (Room r : rooms) {
            r.getBuilding().getId();          // preferDepartmentBuildings, preferTeacherBuilding
            r.getAvailableTimeslots().size(); // roomUnavailable
        }

        // --- Subjects (already partly done via sessions, but load standalone list too) ---
        for (Subject sub : subjects) {
            sub.getDepartment().getId();
            sub.getDepartment().getBuildingsAllowed().size();
        }

        // --- Batches ---
        for (Batch b : batches) {
            b.getDepartment().getId();
            b.getWorkingDays().size();
        }

        // --- Sections ---
        for (ClassSection sec : sections) {
            sec.getBatch().getId();
        }
    }

    // ------------------------------------------------------------------
    // Solver invocation
    // ------------------------------------------------------------------

    private TimetableSolution runSolver(TimetableSolution problem, Integer solvingTimeSeconds) {
        UUID problemId = UUID.randomUUID();
        int timeLimit = (solvingTimeSeconds != null && solvingTimeSeconds > 0) ? solvingTimeSeconds : 30;
        SolverJob<TimetableSolution, UUID> job = solverManager.solveBuilder()
                .withProblemId(problemId)
                .withProblem(problem)
                .withConfigOverride(new SolverConfigOverride<TimetableSolution>()
                        .withTerminationConfig(new TerminationConfig()
                                .withSecondsSpentLimit((long) timeLimit)))
                .run();
        try {
            return job.getFinalBestSolution();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Solver interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Solver execution failed", e.getCause());
        }
    }

    // ------------------------------------------------------------------
    // Persist solution
    // ------------------------------------------------------------------

    @Transactional
    public void persistSolution(Schedule schedule, TimetableSolution solution) {
        HardMediumSoftScore score = solution.getScore();

        // Update schedule metadata
        schedule.setScore(score != null ? score.toString() : null);
        schedule.setScoreExplanation(solutionManager.explain(solution).toString());
        schedule.setStatus(determineStatus(score));
        scheduleRepo.save(schedule);

        // Build a lookup map from the solved clones (planning variables are set on these)
        java.util.Map<Long, ClassSession> solvedById = solution.getSessions().stream()
            .filter(s -> s.getId() != null)
            .collect(java.util.stream.Collectors.toMap(ClassSession::getId, s -> s));

        // Log what the solver actually assigned to each session
        solution.getSessions().forEach(s -> log.info(
            "SOLVED session id={} teacher={} room={} timeslot={}",
            s.getId(),
            s.getTeacher() != null ? s.getTeacher().getId() : "NULL",
            s.getRoom()    != null ? s.getRoom().getId()    : "NULL",
            s.getTimeslot()!= null ? s.getTimeslot().getId() : "NULL"
        ));

        // Reload sessions from DB (avoids Hibernate first-level-cache merge issues with
        // Timefold clones) and apply the solved planning-variable assignments explicitly.
        List<ClassSession> managed = sessionRepo.findByScheduleId(schedule.getId());
        for (ClassSession managed_s : managed) {
            ClassSession solved = solvedById.get(managed_s.getId());
            if (solved != null) {
                managed_s.setTeacher(solved.getTeacher());
                managed_s.setRoom(solved.getRoom());
                managed_s.setTimeslot(solved.getTimeslot());
                managed_s.setLocked(solved.isLocked());
            }
        }
        sessionRepo.saveAll(managed);

        log.info("Schedule [{}] solved. Score: {}", schedule.getId(), score);
    }

    private ScheduleStatus determineStatus(HardMediumSoftScore score) {
        if (score == null) return ScheduleStatus.INFEASIBLE;
        if (score.hardScore() < 0) return ScheduleStatus.PARTIAL;
        return ScheduleStatus.ACTIVE;
    }
}
