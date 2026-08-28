# Scheduling Solver (Timefold)

This document describes the Timefold constraint solver that produces university
timetables in ARARE. The solver consumes a `TimetableSolution` (problem facts +
planning entities) and assigns each `ClassSession` a `teacher`, `room`, and
`timeslot` so that the `HardMediumSoftScore` is maximised.

## 1. Planning domain

### `TimetableSolution` (`solver/TimetableSolution.java`)

The root object handed to the solver. It carries:

| Category | Field | Notes |
|----------|-------|-------|
| **Problem facts** (read-only) | `timeslots`, `rooms`, `teachers`, `subjects`, `batches`, `classSections`, `buildings`, `configs` | Value-range providers, tagged `@ProblemFactCollectionProperty`. |
| | `preAllocationFacts` | `PreAllocationConstraintFact` – partial pins (teacher/room, slot left open). |
| | `teacherBusyIntervals` | Cross-schedule double-booking windows. |
| | `previousAssignments` | Parent/last-run placements, used to minimise churn. |
| | `disruptionFacts` | Sessions to force out of a blocked resource/time during a partial resolve. |
| **Planning entities** | `sessions` (`List<ClassSession>`) | `@PlanningEntityCollectionProperty` – the solver assigns their variables. |
| **Score** | `score` (`HardMediumSoftScore`) | `@PlanningScore`. |

### `ClassSession` (`classsession/ClassSession.java`)

Each `ClassSession` is a `@PlanningEntity` with three planning variables
(`solver/TimetableConstraintProvider` value ranges come from the solution above):

```java
@PlanningVariable(allowsUnassigned = true, valueRangeProviderRefs = {"teacherRange"})
private Teacher teacher;

@PlanningVariable(allowsUnassigned = true, valueRangeProviderRefs = {"roomRange"})
private Room room;

@PlanningVariable(valueRangeProviderRefs = {"timeslotRange"})
private Timeslot timeslot;
```

* `teacher`/`room` allow `null` when `subject.requiresTeacher`/`requiresRoom` is
  false (self-study, off-campus activity, etc.).
* `timeslot` is always required.
* `@PlanningPin isLocked` – when `true`, the solver leaves the variable
  assignments untouched. Used for pre-allocations and for pinning sessions
  outside a partial re-solve scope.
* `allowedTeacherIds` (`@Transient`) – teacher term-allotment gate set by
  `TimetableProblemBuilder`.
* `duration` – slot units, copied from `subject.chunkHours`.
* `getEffectiveStudentCount()` – `section.size` if sectioned, else
  `batch.studentCount`.
* `getEffectiveBatch()` – `batch`, or `section.getBatch()` for lab-split
  sessions. Centralising this keeps the solver, impact, and pre-allocation code
  on one definition.

## 2. Constraint set

`TimetableConstraintProvider.defineConstraints` returns ~40 constraints in three
buckets. Overlap between two sessions is computed by
`overlapsByPlannedDuration` (slot-number based; falls back to start/end time
comparison when `slotNumber` is null). Most binary clash constraints use
`forEachUniquePair` with `Joiners.equal` on the resource **and** on
`timeslot.getDay()`, then an `overlapsByPlannedDuration` filter.

### HARD constraints (must never break)

| Constraint | Intent |
|------------|--------|
| `teacherConflict` | Same teacher, same day, overlapping timeslot. |
| `roomConflict` | Same room, same day, overlapping timeslot. |
| `batchConflict` | Same `effectiveBatch`, same day, overlapping timeslot. |
| `sectionConflict` | Same `section`, same day, overlapping timeslot. |
| `roomCapacityViolation` | `room.capacity < effectiveStudentCount` (penalty = deficit). |
| `roomTypeMismatch` | Room type / lab-subtype does not satisfy `subject`. |
| `singleTeacherPerSubjectSection` | One subject under one `effectiveBatch` may have only one teacher. |
| `teacherNotQualified` | Assigned teacher's `subjects` set lacks the session's subject. |
| `teacherNotAssignedToClass` | Non-locked session whose teacher is outside `allowedTeacherIds`. |
| `teacherUnavailable` | Teacher's `availableTimeslots` set exists and excludes the slot. |
| `roomUnavailable` | Room's `availableTimeslots` set exists and excludes the slot. |
| `breakSlotViolation` | Session placed on a non-CLASS timeslot. |
| `teacherRequiredButMissing` / `teacherAssignedWhenNotRequired` | `requiresTeacher` vs actual teacher presence. |
| `roomRequiredButMissing` | `requiresRoom` vs actual room presence. |
| `labTeacherRequired` | Lab sessions must have a teacher. |
| `labMultiSlotMustHaveConsecutiveStart` | A multi-slot lab must end on a CLASS slot (`supportsSessionEnd`). |
| `multiSlotRequiresSlotNumber` | A multi-slot session needs `slotNumber` set. |
| `batchWorkingDayViolation` | Session scheduled outside the batch's `workingDays`. |
| `scheduleBlockedDayViolation` | Session on a `schedule.blockedDays` entry. |
| `batchDailyClassesCapFromUniversityConfig` | Per-day session count > `UniversityConfig.maxClassesPerDay`. |
| `homeRoomViolation` | Non-lab, non-LAB-room sessions must use the batch's `homeRoom`. |
| `preAllocationViolation` | A `PreAllocationConstraintFact` pin (teacher/room) not honoured. |
| `teacherBusyCrossSchedule` | Teacher already teaching in another ACTIVE schedule. |
| `roomBusyCrossSchedule` | Room already booked in another ACTIVE schedule (physical double-booking across timetables). |
| `disruptionViolation` | Sessions still on a blocked teacher/room/timeslot/day from a disruption. |

### MEDIUM constraints (should not break)

| Constraint | Intent |
|------------|--------|
| `teacherDailyHoursCap` | Sum of `duration` per teacher/day > `maxDailyHours`. |
| `teacherWeeklyHoursCap` | Sum of `duration` per teacher > `maxWeeklyHours`. |
| `teacherConsecutiveClassesCap` | Longest contiguous run of slots > `maxConsecutiveClasses` (`consecutiveSlotExcess`). |
| `mandatoryBatchBreak` | Batch has no covered interval inside the 12:00–14:00 midday window (`hasMiddayBreak`). |
| `avoidStudentIdleGaps` | Consecutive (by slot) sessions of the same `effectiveBatch` with a gap (`hasIdleGap`). |
| `avoidTeacherIdleGaps` | Same for a teacher. |
| `minimizeTeacherBuildingChanges` | Back-to-back sessions of same teacher in different buildings. |
| `preferSplitLabSectionsAtSameTime` | Section-split lab sessions of one subject should share a timeslot. |
| `preferDepartmentBuildings` | Session outside the department's `buildingsAllowed`. |
| `avoidSameSubjectMultipleTimesPerDay` | `count(subject, day) > subject.maxSessionsPerDay`. |

### SOFT constraints (nice to satisfy)

| Constraint | Intent |
|------------|--------|
| `teacherFreeDayPreference` / `batchFreeDayPreference` | Scheduled on the preferred free day. |
| `preferTeacherBuilding` | Room's building not in teacher's `preferredBuildings` (penalty = `movementPenalty`). |
| `minimizeBatchBuildingChanges` | Back-to-back batch sessions in different buildings. |
| `roomStability` | Same subject+batch prefers the same room across sessions. |
| `minimizeMovedSessions` | Session placed differently from its `PreviousAssignment` (churn). |
| `preferNonLabMultiSlotConsecutive` | Non-lab multi-slot session should occupy consecutive slots. |

### Overlap & mirror machinery

* `overlapsByPlannedDuration(a, b)` – slot-indexed overlap (`start < endExclusive`
  on both sides), with a time-based fallback when `slotNumber` is null. This
  avoids the old "always conflict" branch for multi-slot sessions.
* `effectiveBatch(s)` – forwards to `s.getEffectiveBatch()`, used as the join key
  for batch-level clash/density constraints so lab-split sections group with
  their parent batch.
* `movementKey(s)` and `PreviousAssignment.keyFor(s)` are deliberately identical
  (`subject:batch:section:duration`). `minimizeMovedSessions` joins
  `ClassSession` against `PreviousAssignment` on `movementKey` and penalises
  sessions whose `matches()` returns false – this is the "mirror" key that keeps
  the minimal-churn soft rule consistent between the two classes.

## 3. Scoring & termination

* **Score type**: `HardMediumSoftScore` – hard failures dominate everything
  below them; then medium, then soft.
* **Termination**: configured externally via Timefold
  (`termination.spent-limit`, a wall-clock budget) and
  `environment-mode = REPRODUCIBLE` (deterministic, single-threaded solving) so
  identical inputs yield identical schedules.
* **Persistence**: `SolutionPersister.persist` treats a negative hard score as
  *infeasible* but still writes the best-effort placement back and marks the
  schedule `INFEASIBLE` (read-only, not activatable). Only a `null` score
  (unsolved) aborts. See §7.

## 4. Problem construction

### `TimetableProblemBuilder.build` (`solver/TimetableProblemBuilder.java`)

Pipeline producing a `TimetableSolution`:

1. `loadFacts(request)` (the gateway) loads scoped facts.
2. `TimeslotTopologyValidator.validate` checks the CLASS timeslot topology
   against the active `UniversityConfig`.
3. `getOrGenerateSessions` – reuse existing sessions, else generate via
   `StandardSessionGenerator` and persist. Pass `generateIfMissing=false` for the
   read-only `explainSchedule` path so no writes occur.
4. If a parent schedule exists, `ParentLockedSessionApplier.apply` inherits its
   locked placements.
5. `PreAllocationApplier.apply` pins locked pre-allocations (sessions in the
   impacted set are exempted).
6. On a partial resolve, every session is either unlocked (impacted) or pinned
   (non-impacted) so the solver only moves the minimal set.
7. `LazyAssociationInitializer.initialize` force-loads lazy collections.
8. `applyTeacherAllotments` derives `allowedTeacherIds` from `TeacherAssignment`.
9. Assemble the solution: `teacherBusyIntervals` from
   `findTeacherBusyIntervals` and `roomBusyIntervals` from
   `findRoomBusyIntervals` (both scoped by `instituteId`, mirroring each other),
   `previousAssignments` from the parent schedule or a freshly built churn
   baseline, and `disruptionFacts` from the request.

### `JpaProblemDataGateway.loadFacts` (`solver/JpaProblemDataGateway.java`)

Scope resolution by `departmentId` → `instituteId` → all:

* **Subjects**: `findByDepartmentId` / `findByDepartmentInstituteId` /
  `findAll`, then **institute-wide** subjects (no owning department) are always
  added so any batch may take them.
* **Batches**: same scoping; an explicit `batchIds` list further narrows.
* **Sections**: all sections of the in-scope batches.
* **Timeslots**: only `TimeslotType.CLASS` slots are schedulable.
* `resolveAll` rejects unknown IDs rather than silently building a partial
  problem.

### `StandardSessionGenerator` (`solver/StandardSessionGenerator.java`)

For each batch × subject (sorted by `chunkHours` desc), subject to curriculum
scoping (`SubjectOffering` → section curriculum → batch curriculum → department):

* `count = effectiveWeeklyHours / chunkHours`.
* **Lecture**: one `ClassSession` per batch (`batch != null, section == null`),
  `duration = chunkHours`.
* **Lab**: prefers whole-batch labs if any LAB room fits `batch.studentCount`
  and matches the lab subtype. Otherwise, if *every* section can be hosted in
  some LAB room of sufficient capacity, it emits one session **per section**
  (`section != null`). If neither is possible, the lab is **skipped** – emitting
  a known-infeasible whole-batch session is avoided.
* `validateSubjectChunking` enforces `chunkHours > 0` and
  `weeklyHours % chunkHours == 0` (also validated on `SubjectOffering` overrides).

### `TimeslotTopologyValidator` (`solver/TimeslotTopologyValidator.java`)

Asserts the active config is internally consistent (`workingDays.size()` matches
`daysPerWeek`, every configured working day has CLASS timeslots, and
`breakSlotIndices` are non-negative). Throws `IllegalStateException` on mismatch.

### `LazyAssociationInitializer` (`solver/LazyAssociationInitializer.java`)

Force-touches lazy `@ManyToMany`/`@OneToMany` collections (department
buildings, teacher subjects/availability, room building, batch working days /
home room, schedule blocked days, section→batch) **before** the Hibernate
session closes. This prevents `LazyInitializationException` during constraint
evaluation across the session boundary. Note: this is an O(N) per-collection walk
(a "manual N+1 guard") that can be removed once all collections switch to
`@Fetch(SUBSELECT)`.

### `findTeacherBusyIntervals` (`JpaProblemDataGateway`)

Runs `sessionRepo.findActiveCrossScheduleBusyIntervals(scheduleId, teacherIds,
instituteId)` and maps each row to a `TeacherBusyInterval(teacherId, day,
startSlot, endExclusive, startTime, endTime)`. `endExclusive = startSlot +
duration`. These become problem facts feeding `teacherBusyCrossSchedule` so the
solver routes around teacher double-booking across *different* active schedules.

## 5. Pre-allocation & locked-session application

### `PreAllocationApplier` (`solver/PreAllocationApplier.java`)

Iterates locked `PreAllocation`s and matches generated sessions by
`subject.id` + `effectiveBatch.id`:

* **Full pin** (`pa.timeslot != null`): sets `teacher`, `room`, `timeslot` and
  `isLocked = true` on the matching session.
* **Partial pin** (`pa.timeslot == null`): sets `teacher` (and `room`) and emits
  a `PreAllocationConstraintFact(sessionId, teacherId, roomId)`. The
  `preAllocationViolation` HARD constraint then forces the solver to keep those
  values while leaving the slot free. Using a fact (not `@PlanningPin`) avoids
  freezing the timeslot and leaving the session unassigned.
* Sessions whose id is in `impactedSessionIds` are exempt from **all** pins, so a
  disruption that targets a pre-allocated session can still move it.

### `ParentLockedSessionApplier` (`solver/ParentLockedSessionApplier.java`)

On regenerate-from-parent, copies locked parent placements into child sessions
that share a `sessionMatchKey =
subject:batch(≡section.batch):section:duration`. Multiple child sessions per key
are matched positionally (sorted by id) to the parent's locked sessions.

### `PreviousAssignment` (`solver/PreviousAssignment.java`)

A record of a parent/last-run placement: `key, teacherId, roomId, timeslotId`.
`keyFor` mirrors `movementKey`. `minimizeMovedSessions` penalises a session
whose `matches()` is false (i.e. it moved). On a partial resolve,
`TimetableProblemBuilder.buildChurnBaseline` snapshots the *current* assignments
as facts so the re-solve prefers to leave impacted sessions where they are.

## 6. `DisruptionConstraintFact` & partial resolve

`disruptionViolation` joins `DisruptionConstraintFact` × sessions and penalises
HARD any session that still sits on the blocked teacher/room/timeslot/day. This
guarantees a `partialResolve` actually changes the timetable rather than
reporting a no-op success. The facts are produced by
`DisruptionServiceImpl.buildFacts` (see `IMPACT_ANALYSIS.md`).

## 7. `SolutionPersister.persist` (`solver/SolutionPersister.java`)

```java
if (score == null) throw ...                       // truly unsolved
boolean feasible = score.hardScore() >= 0;
managedSchedule.setScore(score.toString());
managedSchedule.setScoreExplanation(solutionManager.explain(solution).toString());
managedSchedule.setStatus(feasible ? DRAFT : INFEASIBLE);
// copy teacher/room/timeslot/locked from solved entities back onto managed rows
sessionRepo.saveAll(managed);
```

Null-safety: only sessions with a non-null id are mapped (`solvedById`), and
managed rows only update when a solved twin exists. The schedule score/explanation
are always persisted, enabling later read-only score breakdown even when
infeasible.

## 8. `explainSchedule` (read-only)

`TimetableSolverService.explainSchedule` calls
`problemBuilder.build(request, /*generateIfMissing=*/false)` so no sessions are
generated or written inside its read-only transaction. It re-attaches the
**latest partial-resolve disruption facts** (`latestPartialResolveFacts`) before
`SolutionManager.explain`, so an infeasible partial resolve is re-scored with the
same constraints and the breakdown matches the persisted score.
