# Pre-Allocation Model

Pre-allocations are manually fixed assignments that the solver must honour (or
strongly prefer) when building a timetable. This document covers the entity, the
request/response shapes, the validation service, and how the solver consumes
them at solve time.

All classes live in `features/preallocation/`.

## 1. `PreAllocation` entity (`preallocation/PreAllocation.java`)

```java
@Entity
@Table(name = "pre_allocations",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"schedule_id", "batch_id", "subject_id", "timeslot_id"}))
public class PreAllocation extends BaseEntity {
    @ManyToOne(optional = false) Schedule schedule;
    @ManyToOne(optional = false) Batch   batch;
    @ManyToOne(optional = false) Subject subject;
    @ManyToOne                     Teacher teacher;   // null if subject needs none
    @ManyToOne                     Room    room;      // null if subject needs none
    @ManyToOne                     Timeslot timeslot; // null ⇒ only teacher/room pinned
    @Column(nullable = false)
    boolean locked = true;          // true ⇒ HARD pin; false ⇒ soft preference
}
```

* The natural key is **(schedule, batch, subject, timeslot)**. A
  `timeslot == null` pre-allocation is unique per (schedule, batch, subject).
* `@PrePersist/@PreUpdate` enforces `schedule`, `batch`, `subject` are present.
* `locked` (default `true`) decides solver treatment: a locked pre-allocation is
  a hard requirement; an unlocked one is treated as a preference. This is
  surfaced at solve time via `PreAllocationApplier` (see §4).

## 2. Request shapes

### `PreAllocationRequest` (`preallocation/PreAllocationRequest.java`)

```java
record PreAllocationRequest(
    @NotNull Long scheduleId,
    @NotNull Long batchId,
    @NotNull Long subjectId,
    Long teacherId, Long roomId, Long timeslotId,
    boolean locked
) {}
```

Used by the single-create endpoint (`PreAllocationServiceImpl.create`).

### `PreAllocationSpec` (`preallocation/PreAllocationSpec.java`)

```java
record PreAllocationSpec(Long batchId, Long subjectId,
                          Long teacherId, Long roomId, Long timeslotId) {}
```

Used by the Schedule-Generator *wizard* **before** the schedule row exists
(carries no `scheduleId`). The service binds these specs to the freshly created
schedule at generate time. `timeslotId` is optional – when null, only the
teacher (and optionally room) are pinned and the solver picks a compatible slot.

## 3. `PreAllocationServiceImpl` (`preallocation/PreAllocationServiceImpl.java`)

### Validation (`buildFromSpec`)

Resolves the batch/subject/teacher/room/timeslot against the catalogs and
rejects anything the solver would find infeasible:

* Teacher present only if `subject.requiresTeacher`; teacher must be in
  `teacher.subjects` (qualified).
* Room type must equal `subject.roomTypeRequired`; lab subtype must match
  `subject.labSubtypeRequired`.
* Room capacity must be ≥ `sectionSizeOf(batch, subject)` (see below).
* Timeslot must be `TimeslotType.CLASS`; teacher/room `availableTimeslots`
  (when non-empty) must contain it.
* `requireTeacherSlotFreeInActiveSchedules` rejects a teacher booked in another
  **active** timetable at the same day/slot (never queue an infeasible
  pre-allocation).

`sectionSizeOf(batch, subject)`: labs are taught per section, so the room must
fit the **largest section's size** (`classSectionRepo.findByBatchId` → max
`size`); otherwise it falls back to `batch.studentCount`.

### Duplication / conflict checks (`validateWithinSchedule`)

Against the schedule's existing pre-allocations (loaded once for `createAll` so
intra-payload collisions are caught in O(N)):

* Identical (schedule, batch, subject, timeslot) → `DuplicateResourceException`.
* Same batch+subject but a *different* teacher → `ResourceConflictException`
  (one-teacher-per-class rule).
* Same teacher, overlapping slots → `ResourceConflictException`
  (`overlaps(timeslot, chunkHours, other.timeslot, other.chunkHours)`).

### `create` vs `createAll`

* `create` validates against freshly loaded existing rows.
* `createAll` loads existing once, then inserts in deterministic order (mirrors
  the solver's `REPRODUCIBLE` mode) so identical wizard payloads yield identical
  rows, appending each saved entity to the in-memory `existing` list so later
  specs catch collisions with earlier ones in the same request.

### `findByScheduleIdWithDetails`

Returns all pre-allocations for a schedule using an EntityGraph
(`findByScheduleIdWithDetails`) so the subject/teacher/room/timeslot are fetched
eagerly for the response DTO.

## 4. How the solver consumes pre-allocations

`PreAllocationApplier.apply` (`solver/PreAllocationApplier.java`) runs during
`TimetableProblemBuilder.build`:

* For each **locked** `PreAllocation` (`findLockedPreAllocations`), match
  generated sessions by `subject.id` + `effectiveBatch.id`:
  * **Full pin** (`pa.timeslot != null`): set `teacher`, `room`, `timeslot`, and
    `isLocked = true`.
  * **Partial pin** (`pa.timeslot == null`): set `teacher` (+`room`) and emit a
    `PreAllocationConstraintFact(sessionId, teacherId, roomId)`. The
    `preAllocationViolation` HARD constraint then forces the solver to keep those
    values while leaving the slot free. This is preferable to `@PlanningPin`
    (which would also freeze the timeslot and risk leaving the session
    unassigned).
* Sessions whose id is in the **impacted** set (partial resolve) are exempt from
  all pins, so a disruption that targets a pre-allocated session can still move
  it.

`PreAllocationConstraintFact` (`solver/PreAllocationConstraintFact.java`) is the
problem-fact record consumed by `preAllocationViolation`.

## 5. Cancellation / cleanup

`CascadeDeletionService` (`cascadedeletion/CascadeDeletionService.java`) exposes
`purgePreAllocationsForBatch/Subject/Teacher/Room/Timeslot/Department` that
delete pre-allocations by their FK columns, plus `purgeScheduleTree` which
removes a schedule and all descendants' pre-allocations (see
`CASCADE_DELETION.md`).
