package com.arare.features.solver;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchType;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.arare.common.enums.RoomType;
import com.arare.common.enums.SchoolDay;
import com.arare.common.enums.TimeslotType;
import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.department.Department;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

class TinyProblemSolveDebugTest {

    private Timeslot slot(Long id, SchoolDay day, int slotNumber) {
        Timeslot ts = Timeslot.builder()
            .day(day).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
            .slotNumber(slotNumber).type(TimeslotType.CLASS).build();
        ts.setId(id);
        return ts;
    }

    private Subject subject(Long id, String name, int maxPerDay) {
        Subject s = Subject.builder()
            .name(name).weeklyHours(3).chunkHours(1)
            .requiresTeacher(true).requiresRoom(true)
            .maxSessionsPerDay(maxPerDay)
            .roomTypeRequired(RoomType.LECTURE)
            .build();
        s.setId(id);
        return s;
    }

    private Teacher teacher(Long id, String name, Subject... subs) {
        Teacher t = Teacher.builder()
            .name(name).maxDailyHours(6).maxWeeklyHours(20).maxConsecutiveClasses(3)
            .subjects(List.of(subs)).build();
        t.setId(id);
        return t;
    }

    private ClassSession session(Long id, Subject subject, Batch batch, Teacher teacher, Timeslot ts) {
        ClassSession s = ClassSession.builder()
            .subject(subject).batch(batch).teacher(teacher).timeslot(ts).room(room()).duration(1).isLocked(false).build();
        s.setId(id);
        return s;
    }

    private Room room() {
        Room r = Room.builder().roomNumber("A101").capacity(60).type(RoomType.LECTURE).build();
        r.setId(1L);
        return r;
    }

    @Test
    void traceSolverProgression() {
        Department dept = Department.builder().name("CSE").code("CS").build();
        dept.setId(1L);

        Subject array = subject(1L, "Array", 2);
        Subject project = subject(2L, "Project", 1);

        Teacher nidhi = teacher(1L, "Nidhi", project);
        Teacher anand = teacher(2L, "Anand", array, project);

        Room room = room();

        Batch batch = Batch.builder().studentCount(60).year(1).section("A").department(dept)
            .workingDays(List.of(SchoolDay.MONDAY, SchoolDay.TUESDAY, SchoolDay.WEDNESDAY))
            .build();
        batch.setId(1L);

        List<Timeslot> slots = List.of(
            slot(1L, SchoolDay.MONDAY, 1),
            slot(2L, SchoolDay.MONDAY, 2),
            slot(3L, SchoolDay.TUESDAY, 1),
            slot(4L, SchoolDay.TUESDAY, 2),
            slot(5L, SchoolDay.WEDNESDAY, 1),
            slot(6L, SchoolDay.WEDNESDAY, 2));

        TimetableSolution problem = new TimetableSolution(
            slots, List.of(room), List.of(nidhi, anand), List.of(array, project),
            List.of(batch), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(
                session(1L, array, batch, anand, null),
                session(2L, array, batch, anand, null),
                session(3L, array, batch, anand, null),
                session(4L, project, batch, nidhi, null),
                session(5L, project, batch, nidhi, null),
                session(6L, project, batch, nidhi, null)),
            null);

SolverConfig config = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(ClassSession.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withPhases(
                new ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE)
                    .withMoveSelectorConfig(new UnionMoveSelectorConfig()
                        .withMoveSelectors(
                            new ChangeMoveSelectorConfig(),
                            new SwapMoveSelectorConfig()))
                    .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(10))));
        Solver<TimetableSolution> solver = SolverFactory.<TimetableSolution>create(config).buildSolver();
        solver.addEventListener(event -> {
            TimetableSolution s = event.getNewBestSolution();
            StringBuilder sb = new StringBuilder("BEST " + s.getScore() + " | ");
            for (ClassSession cs : s.getSessions()) {
                sb.append(cs.getSubject().getName()).append(":")
                  .append(cs.getTimeslot() != null ? cs.getTimeslot().getDay() + "#" + cs.getTimeslot().getSlotNumber() : "un")
                  .append(" ");
            }
            System.out.println(sb);
        });
        solver.solve(problem);
        System.out.println("== DONE ==");

        // Full constraint breakdown of the stuck solution.
        TimetableSolution stuck = new TimetableSolution(
            slots, List.of(room), List.of(nidhi, anand), List.of(array, project),
            List.of(batch), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(
                session(1L, array, batch, anand, slots.get(0)),
                session(2L, array, batch, anand, slots.get(1)),
                session(3L, array, batch, anand, slots.get(2)),
                session(4L, project, batch, nidhi, slots.get(3)),
                session(5L, project, batch, nidhi, slots.get(4)),
                session(6L, project, batch, nidhi, slots.get(5))),
            null);
        ai.timefold.solver.core.api.score.ScoreExplanation<TimetableSolution, HardMediumSoftScore> explanation =
            SolutionManager.<TimetableSolution, HardMediumSoftScore>create(SolverFactory.<TimetableSolution>create(config))
                .explain(stuck);
        System.out.println("STUCK SCORE: " + explanation.getScore());
        System.out.println("EXPLANATION:\n" + explanation.getSummary());

        // Manually apply the swap that SHOULD reach the ideal:
        // Array:Mon#2 <-> Project:Wed#1
        TimetableSolution swapped = new TimetableSolution(
            slots, List.of(room), List.of(nidhi, anand), List.of(array, project),
            List.of(batch), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(
                session(1L, array, batch, anand, slots.get(0)),
                session(2L, array, batch, anand, slots.get(4)),
                session(3L, array, batch, anand, slots.get(2)),
                session(4L, project, batch, nidhi, slots.get(3)),
                session(5L, project, batch, nidhi, slots.get(1)),
                session(6L, project, batch, nidhi, slots.get(5))),
            null);
        var swappedScore = SolutionManager.<TimetableSolution, HardMediumSoftScore>create(
                SolverFactory.<TimetableSolution>create(config)).update(swapped);
        System.out.println("SWAPPED (A:Mon#1,A:Wed#1,A:Tue#1 | P:Tue#2,P:Mon#2,P:Wed#2) SCORE: " + swappedScore);
    }

    @Test
    void startFromIdealAndSeeIfKept() {
        Department dept = Department.builder().name("CSE").code("CS").build();
        dept.setId(1L);

        Subject array = subject(1L, "Array", 2);
        Subject project = subject(2L, "Project", 1);

        Teacher nidhi = teacher(1L, "Nidhi", project);
        Teacher anand = teacher(2L, "Anand", array, project);

        Room room = room();

        Batch batch = Batch.builder().studentCount(60).year(1).section("A").department(dept)
            .workingDays(List.of(SchoolDay.MONDAY, SchoolDay.TUESDAY, SchoolDay.WEDNESDAY))
            .build();
        batch.setId(1L);

        List<Timeslot> slots = List.of(
            slot(1L, SchoolDay.MONDAY, 1),
            slot(2L, SchoolDay.MONDAY, 2),
            slot(3L, SchoolDay.TUESDAY, 1),
            slot(4L, SchoolDay.TUESDAY, 2),
            slot(5L, SchoolDay.WEDNESDAY, 1),
            slot(6L, SchoolDay.WEDNESDAY, 2));

        TimetableSolution ideal = new TimetableSolution(
            slots, List.of(room), List.of(nidhi, anand), List.of(array, project),
            List.of(batch), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(
                session(1L, array, batch, anand, slots.get(0)),
                session(2L, array, batch, anand, slots.get(4)),
                session(3L, array, batch, anand, slots.get(2)),
                session(4L, project, batch, nidhi, slots.get(3)),
                session(5L, project, batch, nidhi, slots.get(1)),
                session(6L, project, batch, nidhi, slots.get(5))),
            null);

        SolverConfig config = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(ClassSession.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(5)));

        Solver<TimetableSolution> solver = SolverFactory.<TimetableSolution>create(config).buildSolver();
        TimetableSolution result = solver.solve(ideal);
        System.out.println("STARTED FROM IDEAL -> RESULT: " + result.getScore());
        for (ClassSession cs : result.getSessions()) {
            System.out.println("  " + cs.getSubject().getName() + " -> "
                + (cs.getTimeslot() != null ? cs.getTimeslot().getDay() + "#" + cs.getTimeslot().getSlotNumber() : "un")
                + " [" + cs.getTeacher().getName() + "]");
        }
    }

    @Test
    void scoreIdealArrangement() {
        Department dept = Department.builder().name("CSE").code("CS").build();
        dept.setId(1L);

        Subject array = subject(1L, "Array", 2);
        Subject project = subject(2L, "Project", 1);

        Teacher nidhi = teacher(1L, "Nidhi", project);
        Teacher anand = teacher(2L, "Anand", array, project);

        Room room = room();

        Batch batch = Batch.builder().studentCount(60).year(1).section("A").department(dept)
            .workingDays(List.of(SchoolDay.MONDAY, SchoolDay.TUESDAY, SchoolDay.WEDNESDAY))
            .build();
        batch.setId(1L);

        List<Timeslot> slots = List.of(
            slot(1L, SchoolDay.MONDAY, 1),
            slot(2L, SchoolDay.MONDAY, 2),
            slot(3L, SchoolDay.TUESDAY, 1),
            slot(4L, SchoolDay.TUESDAY, 2),
            slot(5L, SchoolDay.WEDNESDAY, 1),
            slot(6L, SchoolDay.WEDNESDAY, 2));

        // IDEAL: Mon(A,P), Tue(A,P), Wed(A,P)
        TimetableSolution ideal = new TimetableSolution(
            slots,
            List.of(room),
            List.of(nidhi, anand),
            List.of(array, project),
            List.of(batch),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                session(1L, array, batch, anand, slots.get(0)),
                session(2L, array, batch, anand, slots.get(2)),
                session(3L, array, batch, anand, slots.get(4)),
                session(4L, project, batch, nidhi, slots.get(1)),
                session(5L, project, batch, nidhi, slots.get(3)),
                session(6L, project, batch, nidhi, slots.get(5))),
            null);

        SolverConfig config = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(ClassSession.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

        SolverFactory<TimetableSolution> sf = SolverFactory.<TimetableSolution>create(config);
        Object updated = SolutionManager.<TimetableSolution, HardMediumSoftScore>create(sf)
            .update(ideal);
        System.out.println("IDEAL (Mon:A+P, Tue:A+P, Wed:A+P) SCORE: " + updated);
    }
}