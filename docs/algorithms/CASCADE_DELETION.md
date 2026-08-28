# Cascade Deletion

`CascadeDeletionService` (`cascadedeletion/CascadeDeletionService.java`) cleans
up dependent rows when an entity is deleted. It deliberately performs all
cleanup in **Java via JPA repository methods** rather than native SQL, so
persistence behaviour (cascades, `@PreRemove` hooks, auditing) stays consistent
with the rest of the application and the code remains database-portable.

All methods are `@Transactional`.

## 1. Schedule subtree purge – `purgeScheduleTree(scheduleId)`

Deletes a schedule **and every descendant schedule** (schedules created from a
parent), in cascade order:

1. `collectScheduleSubtree(rootId)` does a recursive walk via
   `scheduleRepo.findByParentScheduleId`, accumulating `rootId` + all children
   depth-first.
2. For each collected id, delete dependent rows first:
   * `sessionRepo.deleteByScheduleId(id)` – all class sessions.
   * `preAllocationRepo.deleteByScheduleId(id)` – all pre-allocations.
3. Finally `scheduleRepo.deleteAllByIdInBatch(ids)` removes the schedule rows
   themselves (batched delete).

Ordering matters: child (session/pre-allocation) rows are removed **before** the
schedule rows they reference, avoiding FK-violation aborts.

## 2. Pre-allocation purges

Fine-grained deletes keyed by the pre-allocation's FK columns (each a repository
`deleteBy…` method):

* `purgePreAllocationsForBatch(batchId)`
* `purgePreAllocationsForSubject(subjectId)`
* `purgePreAllocationsForTeacher(teacherId)`
* `purgePreAllocationsForRoom(roomId)`
* `purgePreAllocationsForTimeslot(timeslotId)`
* `purgePreAllocationsForDepartment(departmentId)`

These let the UI remove a single dimension (e.g. "all pre-allocations for this
teacher") without touching the rest of the timetable.

## 3. Availability / event detachment

Timeslot/room/teacher deletion must also scrub join-table references so no
orphaned FK remains:

* `detachTimeslot(timeslotId)`:
  * `teacherRepo.deleteTeacherAvailabilityByTimeslotId` – remove the timeslot from
    teacher availability.
  * `roomRepo.deleteRoomAvailabilityByTimeslotId` – same for room availability.
  * `eventRepo.deleteEventAffectedTimeslotsByTimeslotId` – unlink the timeslot
    from any `Event`.
* `detachRoomFromEvents(roomId)` –
  `eventRepo.deleteEventAffectedRoomsByRoomId`.
* `detachTeacherFromEvents(teacherId)` –
  `eventRepo.deleteEventAffectedTeachersByTeacherId`.

## 4. Why Java-side cleanup

* **No native SQL**: every delete goes through a Spring Data JPA repository
  method, keeping the deletion logic in the domain layer and portable across
  Hibernate dialects.
* **FK ordering in memory**: the service decides the *sequence* (children before
  parents, join rows before the referenced entity) explicitly, rather than
  relying on DB-level cascades that could vary by provider.
* **Transactional boundary**: all deletions for a single call happen in one
  transaction, so a failure rolls back the whole operation cleanly.

The pre-allocation purge methods are also used by the data-import and
pre-allocation management flows (see `PREALLOCATION.md` and `DATA_IMPORT.md`).
