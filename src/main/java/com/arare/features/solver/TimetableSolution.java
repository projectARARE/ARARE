package com.arare.features.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import com.arare.features.batch.Batch;
import com.arare.features.building.Building;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.universityconfig.UniversityConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * The Timefold planning solution for ARARE timetable scheduling.
 *
 * <p>This class is the root object handed to the Timefold solver. It contains:
 * <ul>
 *   <li><b>Problem facts</b> – data the solver reads but does not modify
 *       ({@code @ProblemFactCollectionProperty})</li>
 *   <li><b>Planning entities</b> – objects whose planning variables the solver assigns
 *       ({@code @PlanningEntityCollectionProperty})</li>
 *   <li><b>Planning score</b> – how good the current solution is
 *       ({@code @PlanningScore})</li>
 * </ul>
 * </p>
 *
 * <p><b>Score type: {@link HardMediumSoftScore}</b>
 * <ul>
 *   <li>Hard  – must never be broken (clash, capacity violation, etc.)</li>
 *   <li>Medium – should not be broken (teacher overload, idle gaps, etc.)</li>
 *   <li>Soft   – nice to satisfy (free-day preference, building preference, etc.)</li>
 * </ul>
 * </p>
 *
 * <p><b>Value range providers</b> are referenced by {@code @PlanningVariable} in
 * {@link ClassSession} using the IDs defined here.</p>
 */
@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimetableSolution {

    // ------------------------------------------------------------------
    // Problem Facts – the solver reads these; they never change
    // ------------------------------------------------------------------

    /** All schedulable timeslots (type == CLASS). */
    @ValueRangeProvider(id = "timeslotRange")
    @ProblemFactCollectionProperty
    private List<Timeslot> timeslots;

    @ValueRangeProvider(id = "roomRange")
    @ProblemFactCollectionProperty
    private List<Room> rooms;

    @ValueRangeProvider(id = "teacherRange")
    @ProblemFactCollectionProperty
    private List<Teacher> teachers;

    @ProblemFactCollectionProperty
    private List<Subject> subjects;

    @ProblemFactCollectionProperty
    private List<Batch> batches;

    @ProblemFactCollectionProperty
    private List<ClassSection> classSections;

    @ProblemFactCollectionProperty
    private List<Building> buildings;

    /** Active university configuration (used by constraints). */
    @ProblemFactCollectionProperty
    private List<UniversityConfig> configs;

    // ------------------------------------------------------------------
    // Planning Entities – the solver assigns teacher/room/timeslot here
    // ------------------------------------------------------------------

    @PlanningEntityCollectionProperty
    private List<ClassSession> sessions;

    // ------------------------------------------------------------------
    // Score
    // ------------------------------------------------------------------

    @PlanningScore
    private HardMediumSoftScore score;
}
