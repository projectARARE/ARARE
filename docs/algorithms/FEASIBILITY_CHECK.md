# Feasibility Check (Pre-Solve Validator)

`FeasibilityCheckService.check` (`schedule/FeasibilityCheckService.java`) is the
**constraint-propagation** layer that runs *before* the Timefold solver. It is
O(batches × subjects) – fast enough to run interactively in the UI before
clicking "Generate Schedule". Its purpose:

1. Detect **hard errors** that guarantee infeasibility (e.g. a subject with no
   qualified teacher).
2. Surface **warnings** that usually yield a poor score (e.g. more sessions than
   available teacher-slots).

A `FeasibilityCheckResult` is feasible iff `errorCount == 0`
(`feasible = errorCount == 0`). Issues are sorted errors-first then warnings.

## 1. Scope loading

Mirrors `JpaProblemDataGateway.loadFacts` scoping:

* Batches: narrow by `batchIds` → `departmentId` → `instituteId` → all.
* Teachers / Rooms: narrow by explicit id list → all.
* Subjects: `findByDepartmentId` / `findByDepartmentInstituteId` / all-filtered;
  **institute-wide** subjects (null department) are always added.

If no batches ⇒ single ERROR and abort. If no CLASS timeslots ⇒ single ERROR and
abort.

## 2. Per-subject checks

For every subject:

* `chunkHours <= 0` ⇒ **ERROR** ("invalid chunkHours").
* `weeklyHours % chunkHours != 0` ⇒ **ERROR** (silent under-scheduling).
* `requiresTeacher` and no teacher has it in `teacher.subjects` ⇒ **ERROR**
  ("No qualified teacher").

### Multi-slot / contiguous-run check

If the max `chunkHours` (`maxChunkUnits`) > 1:

* At least one CLASS timeslot must have a `slotNumber` ⇒ else **ERROR**.
* Compute `longestConsecutiveClassRun` (longest run of contiguous slots per day,
  by `slotNumber` or by start/end adjacency).
* Convert that slot run to hours: `hoursPerSlot = max slot duration in hours`
  (min 1h, conservative). `maxConsecutiveHours = run × hoursPerSlot`.
* If `maxConsecutiveHours < maxChunkUnits` ⇒ **ERROR** (a subject's chunk cannot
  fit in any contiguous slot run).

## 3. Lab room-type check

For each lab subject that `requiresRoom`: if no room has
`room.type == subject.roomTypeRequired` ⇒ **ERROR**.

## 4. Capacity checks

* `totalSessions = Σ computeSessionCountForBatch(...)` (mirrors
  `StandardSessionGenerator`'s curriculum logic, including lab per-section
  multiplication).
* **Per-batch capacity**: if `batchSessions > classTimeslotCount` ⇒ **ERROR**
  (infeasible for that batch).
* **Global teacher capacity**: if `totalSessions > teachers.size() ×
  classTimeslotCount` ⇒ **WARNING** (some sessions may stay unassigned).

`computeSessionCountForBatch` honours the same fallback chain as the generator:
explicit `SubjectOffering` list → section curriculum → batch curriculum →
department-offered. Lab sessions multiply by the number of *offering* sections
(or fall back to whole-batch count when no section offers the split).

## 5. Per-subject × timeslot check

For each batch × subject (skipping subjects owned by a different department;
institute-wide ones are always eligible):

* `sessionsPerBatch = weeklyHours / chunkHours`.
* If lab and the batch has `sectionCount > 0`, multiply by `sectionCount`.
* If `sessionsPerBatch > classTimeslotCount` ⇒ **ERROR** ("infeasible").

This lab multiplier is critical: without it a lab needing more per-week sessions
than *section-level* timeslots would pass incorrectly.

## 6. Pre-allocation correctness (`checkPreAllocations`)

For each `PreAllocationSpec` in the request:

* Missing batch/subject ⇒ **ERROR**; teacher/room outside scope ⇒ **ERROR**.
* Teacher not qualified for the subject ⇒ **ERROR**.
* Room type/lab-subtype mismatch ⇒ **ERROR**; missing/non-CLASS timeslot ⇒
  **ERROR**.
* **Two different teachers** for the same (batch, subject) ⇒ **ERROR** (mirrors
  `singleTeacherPerSubjectSection`).
* Same teacher in two **overlapping** slots ⇒ **ERROR**.
* Cross-schedule gate: teacher already booked in another **active** timetable at
  that day/slot ⇒ **ERROR** (`sessionRepo.findActiveCrossScheduleSessions`).

## 7. Teacher allotment resolution (`checkTeacherAllotments`)

Term `TeacherAssignment`s are grouped by (batch, subject). For each group:

* More than one distinct allotted teacher ⇒ **ERROR** (one-teacher-per-class
  would make it infeasible).
* The single allotted teacher not in the schedule's `requestedTeacherIds` scope ⇒
  **ERROR** ("Add the teacher to the scope or change the allotment").

This mirrors the `teacherNotAssignedToClass` HARD constraint: an allotment must
resolve to exactly one in-scope teacher, otherwise the solver HARD-rejects every
other teacher and the schedule is unsolvable.

## 8. Severity & outcome

* `error(...)` → `FeasibilityIssue.Severity.ERROR`
* `warn(...)` → `FeasibilityIssue.Severity.WARNING`
* `result(...)` sorts issues (errors first), counts them, and sets
  `feasible = errors == 0`.

The result reports `totalSessions` and `classTimeslotCount` so the UI can show
utilisation at a glance.
