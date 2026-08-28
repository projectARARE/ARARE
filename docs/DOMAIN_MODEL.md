# ARARE Domain Model

This document describes the JPA domain model. All entities extend
`com.arare.common.BaseEntity` (which provides `id`, `createdAt`, `updatedAt`,
`version`, and ID-based `equals`/`hashCode`) **except `ClassSession`**, which
declares its own `@PlanningId @Id` for Timefold.

Tables are created by Flyway migrations (`src/main/resources/db/migration`,
`V1`…`V11`); `spring.jpa.hibernate.ddl-auto=validate` in production, so the
schema is owned by the migrations, not by Hibernate auto-DDL.

Relationship conventions:
- `@ManyToOne` sides are the owning side (they hold the FK column).
- `@ManyToMany` sides listed below create a join table unless noted.
- `@ElementCollection` creates a separate collection table.

---

## Entities

### Institute
Table `institutes`. A constituent institute within the university. Most
deployments have exactly one; multiple institutes share the parent university's
term calendar.

| Field | Type | Notes |
| --- | --- | --- |
| name | `String` | `@NotBlank`, `@Size(120)`, **unique** |
| code | `String` | `@NotBlank`, `@Pattern([A-Za-z0-9_-]+)`, `@Size(20)`, **unique**, stored upper-cased |
| description | `String` | optional, `@Size(255)` |

Normalized on persist/update (trim; code upper-cased).

### Department
Table `departments`. Academic department (e.g. CSE, IT). Owns allowed buildings.

| Field | Type | Notes |
| --- | --- | --- |
| name | `String` | `@NotBlank`, `@Size(120)`, **unique** |
| code | `String` | `@NotBlank`, `@Size(20)`, **unique** |
| institute | `Institute` | `@ManyToOne(optional=false)`, FK `institute_id` |
| buildingsAllowed | `List<Building>` | `@ManyToMany` join table `department_buildings` (soft constraint) |

### Building
Table `buildings`. Physical building. Unique on `name`.

| Field | Type | Notes |
| --- | --- | --- |
| name | `String` | `@NotBlank`, **unique** |
| location | `String` | optional |

### Room
Table `rooms`. Physical room. Unique on (`building_id`, `room_number`).

| Field | Type | Notes |
| --- | --- | --- |
| building | `Building` | `@ManyToOne(optional=false)`, FK `building_id` |
| roomNumber | `String` | `@NotBlank`, `@Size(30)`, stored upper-cased |
| type | `RoomType` | `@NotNull` enum (`LECTURE`/`LAB`) |
| labSubtype | `LabSubtype` | required when `type==LAB`, else null |
| capacity | `int` | `@Min(1)` |
| availableTimeslots | `List<Timeslot>` | `@ManyToMany` join table `room_availability`; empty = available for all CLASS slots |

Hard constraints (enforced by solver): room not double-booked, capacity ≥ student
count, `type`/`labSubtype` match the subject. Validation throws if `LAB` has no
`labSubtype`, or non-LAB sets one.

### Batch
Table `batches`. Student cohort (e.g. CSE-2A). Unique on
(`department_id`, `year`, `section`).

| Field | Type | Notes |
| --- | --- | --- |
| department | `Department` | `@ManyToOne(optional=false)`, FK `department_id` |
| year | `int` | `@Min(1)` academic year |
| section | `String` | `@NotBlank`, `@Size(10)`, default `"A"`, upper-cased |
| studentCount | `int` | `@Min(1)` |
| workingDays | `List<SchoolDay>` | `@ElementCollection` table `batch_working_days` |
| preferredFreeDay | `SchoolDay` | optional soft constraint |
| homeRoom | `Room` | `@ManyToOne`, FK `home_room_id` (nullable). See "Cascade handling" below. |
| subjects | `List<Subject>` | `@ManyToMany` table `batch_subjects`; empty = inherit department curriculum |

Hard constraint: no two sessions for the same batch at the same timeslot.

### ClassSection
Table `class_sections`. Sub-division of a `Batch` for lab splits (smaller than
full batch). Unique on (`batch_id`, `label`).

| Field | Type | Notes |
| --- | --- | --- |
| batch | `Batch` | `@ManyToOne(optional=false)`, FK `batch_id` |
| label | `String` | `@NotBlank`, `@Size(10)`, default `"A"`, upper-cased |
| size | `int` | `@Min(1)` student count for this section |
| subjects | `List<Subject>` | `@ManyToMany` table `class_section_subjects`; empty = inherit |

Each section generates its own `ClassSession` for lab subjects.

### Teacher
Table `teachers`. Faculty member.

| Field | Type | Notes |
| --- | --- | --- |
| employeeId | `String` | `@Size(40)`, **unique**, natural key for CSV imports, optional |
| name | `String` | `@NotBlank`, `@Size(120)` |
| subjects | `List<Subject>` | `@ManyToMany` table `teacher_subjects` (qualified subjects) |
| availableTimeslots | `List<Timeslot>` | `@ManyToMany` table `teacher_availability`; empty = available all CLASS |
| preferredBuildings | `List<Building>` | `@ManyToMany` table `teacher_preferred_buildings` |
| maxDailyHours | `int` | `@Min(1)`, default 6 (medium constraint) |
| maxWeeklyHours | `int` | `@Min(1)`, default 20 (medium constraint) |
| maxConsecutiveClasses | `int` | `@Min(1)`, default 3 (medium constraint) |
| movementPenalty | `int` | `@Min(0)`, default 1 (building-change soft weight) |
| preferredFreeDay | `SchoolDay` | optional soft constraint |

Hard constraints: teacher not double-booked, available at slot, qualified for
subject. Collections are de-duplicated in place on persist/update.

### Subject
Table `subjects`. A course component. Lectures and labs for the same course are
separate `Subject` rows (e.g. "DSA Lecture" vs "DSA Lab") to allow independent
room-type/teacher assignment.

| Field | Type | Notes |
| --- | --- | --- |
| name | `String` | `@NotBlank` |
| code | `String` | `@Size(30)`, upper-cased, optional |
| department | `Department` | `@ManyToOne` (nullable → institute-wide subject) |
| weeklyHours | `int` | `@Min(1)` contact load in slot units |
| chunkHours | `int` | `@Min(1)`, default 1, session size in slot units (sessions = weeklyHours/chunkHours) |
| roomTypeRequired | `RoomType` | `@NotNull`, default `LECTURE` |
| labSubtypeRequired | `LabSubtype` | null unless lab |
| isLab | `boolean` | default false |
| requiresTeacher | `boolean` | default true (false → solver may leave teacher null) |
| requiresRoom | `boolean` | default true (false → solver may leave room null) |
| minGapBetweenSessions | `int` | `@Min(0)`, default 0, spread soft constraint |
| maxSessionsPerDay | `int` | `@Min(1)`, default 1, cognitive-load constraint |

Validation enforces invariants (weeklyHours divisible by chunkHours, lab↔LAB
consistency).

### SubjectOffering
Table `subject_offerings`. The "offered this term" layer: which batch/section
takes which subject. Exactly one of `batch`/`section` must be set.

| Field | Type | Notes |
| --- | --- | --- |
| subject | `Subject` | `@ManyToOne(optional=false)`, FK `subject_id` |
| batch | `Batch` | `@ManyToOne` (optional) |
| section | `ClassSection` | `@ManyToOne` (optional) |
| weeklyHours | `Integer` | `@Min(1)`, optional per-offering override |
| elective | `boolean` | default false |

Backward compatible: when a batch/section has no offerings, the session generator
falls back to the legacy `batch.subjects` / `section.subjects`.

### TeacherAssignment
Table `teacher_assignments`. The "allotted" layer: which teacher actually teaches
which subject for which batch/section this term. Exactly one of `batch`/`section`
must be set. Treated as a **hard constraint** by the solver (unless no allotment
exists, then it falls back to qualified teachers).

| Field | Type | Notes |
| --- | --- | --- |
| teacher | `Teacher` | `@ManyToOne(optional=false)` |
| subject | `Subject` | `@ManyToOne(optional=false)` |
| batch | `Batch` | `@ManyToOne` (optional) |
| section | `ClassSection` | `@ManyToOne` (optional) |
| weeklyHours | `Integer` | `@Min(1)`, optional workload override |
| priority | `int` | `@Min(0)`, default 1 |
| notes | `String` | `@Size(400)` |

### Timeslot
Table `timeslots`. A fixed period on a school day. Global (shared by all
departments/rooms). Unique on (`day`, `start_time`, `end_time`).

| Field | Type | Notes |
| --- | --- | --- |
| day | `SchoolDay` | `@NotNull` |
| startTime | `LocalTime` | `@NotNull` |
| endTime | `LocalTime` | `@NotNull`, must be after startTime |
| slotNumber | `Integer` | `@Positive`, optional |
| type | `TimeslotType` | `@NotNull`, default `CLASS` (`CLASS`/`BREAK`/`BLOCKED`) |

`BREAK`/`BLOCKED` act as hard-constraint fences; solver only assigns to `CLASS`.

### ClassSession
Table `class_sessions`. **The core Timefold planning entity.** One teaching slot
to be assigned a `teacher`, `room`, `timeslot`. Does **not** extend `BaseEntity`
(uses `@PlanningId @Id`).

| Field | Type | Notes |
| --- | --- | --- |
| id | `Long` | `@PlanningId @Id` |
| subject | `Subject` | `@ManyToOne(optional=false)` |
| batch | `Batch` | `@ManyToOne` (null when section lab split) |
| section | `ClassSection` | `@ManyToOne` (null for lecture/batch sessions) |
| schedule | `Schedule` | `@ManyToOne(optional=false)` |
| duration | `int` | `@Min(1)`, default 1 (= subject.chunkHours) |
| isLocked | `boolean` | `@PlanningPin` default false; locked = solver must not change |
| teacher | `Teacher` | `@PlanningVariable(allowsUnassigned=true)` |
| room | `Room` | `@PlanningVariable(allowsUnassigned=true)` |
| timeslot | `Timeslot` | `@PlanningVariable` (required) |
| allowedTeacherIds | `List<Long>` | `@Transient` — derived per problem build (allotment gate) |

Invariant: exactly one of `batch`/`section` must be set. `getEffectiveBatch()`
and `getEffectiveStudentCount()` centralize the lab-split resolution.

### Event
Table `events`. A real-world disruption. Not a solver entity; drives partial
re-optimization.

| Field | Type | Notes |
| --- | --- | --- |
| title | `String` | `@NotBlank` |
| type | `EventType` | `@NotNull` enum |
| startDate / endDate | `LocalDate` | `@NotNull`, endDate ≥ startDate |
| description | `String` | optional TEXT |
| affectedRooms | `List<Room>` | `@ManyToMany` table `event_affected_rooms` |
| affectedTeachers | `List<Teacher>` | `@ManyToMany` table `event_affected_teachers` |
| affectedTimeslots | `List<Timeslot>` | `@ManyToMany` table `event_affected_timeslots` |

### AcademicTerm
Table `academic_terms`. A semester/trimester for temporal versioning of schedules.

| Field | Type | Notes |
| --- | --- | --- |
| name | `String` | `@NotBlank` (e.g. "Semester 1 2025–26") |
| academicYear | `String` | optional (e.g. "2025-26") |
| startDate / endDate | `LocalDate` | `@NotNull`, endDate ≥ startDate |
| examPeriodStart / examPeriodEnd | `LocalDate` | optional |
| status | `AcademicTermStatus` | default `UPCOMING` |
| description | `String` | optional TEXT |

### Schedule
Table `schedules`. A generated/edited timetable.

| Field | Type | Notes |
| --- | --- | --- |
| name | `String` | `@NotBlank` |
| scope | `ScheduleScope` | default `DEPARTMENT` |
| status | `ScheduleStatus` | default `DRAFT` |
| instituteId | `Long` | home institute for INSTITUTE scope; null = university-wide |
| parentSchedule | `Schedule` | `@ManyToOne` FK `parent_schedule_id` (version tree) |
| score | `String` | solver score text |
| scoreExplanation | `String` | TEXT |
| blockedDays | `List<SchoolDay>` | `@ElementCollection` table `schedule_blocked_days` (hard fence) |

Activating a schedule archives any other `ACTIVE` schedule in the same institute
scope. `INFEASIBLE`/`ARCHIVED` schedules cannot be activated.

### PreAllocation
Table `pre_allocations`. A manually fixed assignment the solver must honour.
Unique on (`schedule_id`, `batch_id`, `subject_id`, `timeslot_id`).

| Field | Type | Notes |
| --- | --- | --- |
| schedule | `Schedule` | `@ManyToOne(optional=false)` |
| batch | `Batch` | `@ManyToOne(optional=false)` |
| subject | `Subject` | `@ManyToOne(optional=false)` |
| teacher | `Teacher` | `@ManyToOne` (null if subject needs none) |
| room | `Room` | `@ManyToOne` (null if subject needs none) |
| timeslot | `Timeslot` | `@ManyToOne` (null = only teacher/room pinned) |
| locked | `boolean` | default true; false = strong preference only |

Before solving, `PreAllocationApplier` finds the matching `ClassSession`, sets
its `teacher`/`room`/`timeslot`, and marks it `isLocked`.

### SolveJob
Table `solve_jobs`. Durable record of an async solve request.

| Field | Type | Notes |
| --- | --- | --- |
| jobType | `SolveJobType` | `GENERATE` / `PARTIAL_RESOLVE` |
| scheduleId | `Long` | FK-like (not a JPA relation) |
| problemId | `UUID` | identifies the live Timefold solver for cancellation |
| status | `SolveJobStatus` | `QUEUED`/`RUNNING`/`SUCCEEDED`/`FAILED`/`CANCELLED` |
| solvingTimeSeconds | `Integer` | solver time limit |
| departmentId / instituteId | `Long` | scope snapshot |
| batchIdsCsv / teacherIdsCsv / roomIdsCsv | `String` | CSV id snapshot (GENERATE) |
| impactedSessionIdsCsv | `String` | CSV (PARTIAL_RESOLVE) |
| disruptionFactsCsv | `String` | `"TYPE:id:day"` entries (PARTIAL_RESOLVE w/ disruption) |
| score / bestScore | `String` | outcome / live best score |
| errorMessage | `String` | TEXT on failure |
| elapsedMillis | `Long` | |
| startedAt / finishedAt | `LocalDateTime` | |

Status transitions are performed by guarded bulk `UPDATE`
(`SolveJobRepository.transitionTerminal`), which does **not** bump `@Version`.

### UniversityConfig
Table `university_configs`. Global scheduling knobs; only **one active** record
is expected (`active` flag; migration `V3` enforces single active config).

| Field | Type | Notes |
| --- | --- | --- |
| active | `boolean` | default true |
| daysPerWeek | `int` | `@Min(1) @Max(7)`, default 5 |
| timeslotsPerDay | `int` | `@Min(1)`, default 8 (incl. breaks) |
| maxClassesPerDay | `int` | `@Min(1)`, default 6, ≤ timeslotsPerDay |
| breakSlotIndices | `List<Integer>` | `@ElementCollection` table `config_break_indices` |
| workingDays | `List<SchoolDay>` | `@ElementCollection` table `config_working_days`; size must equal `daysPerWeek` |

---

## Enums

| Enum | Values | Meaning |
| --- | --- | --- |
| `RoomType` | `LECTURE`, `LAB` | Required room category for a subject/session |
| `LabSubtype` | `COMPUTER_LAB`, `ELECTRONICS_LAB`, `CHEMISTRY_LAB`, `PHYSICS_LAB`, `MECHANICAL_LAB`, `CIVIL_LAB`, `NETWORK_LAB`, `GENERAL_LAB` | Specific lab kind; matches `Room.labSubtype` |
| `EventType` | `EXAM`, `FESTIVAL`, `MAINTENANCE`, `TEACHER_LEAVE`, `GUEST_LECTURE`, `SPORTS_DAY`, `SEMINAR`, `HOLIDAY`, `OTHER` | Kind of disruption event |
| `SchoolDay` | `MONDAY`…`SUNDAY` | Day-of-week for timeslots/availability/blocked days (5/6/7-day calendars) |
| `TimeslotType` | `CLASS`, `BREAK`, `BLOCKED` | `CLASS` assignable; `BREAK`/`BLOCKED` are hard fences |
| `ScheduleStatus` | `DRAFT`, `ACTIVE`, `ARCHIVED`, `PARTIAL`, `INFEASIBLE` | Lifecycle of a schedule |
| `ScheduleScope` | `DEPARTMENT`, `INSTITUTE`, `UNIVERSITY` | Scheduling breadth |
| `AcademicTermStatus` | `UPCOMING`, `ACTIVE`, `CLOSED`, `ARCHIVED` | Term lifecycle |
| `SolveJobStatus` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED` | Async solve lifecycle |
| `SolveJobType` | `GENERATE`, `PARTIAL_RESOLVE` | Full build vs. re-solve of impacted sessions |
| `DisruptionType` | `TEACHER_UNAVAILABLE`, `ROOM_UNAVAILABLE`, `TIMESLOT_BLOCKED`, `SESSION_CANCELLED`, `SPECIAL_EVENT` | Kind of disruption analysed by the impact engine |

---

## Cascade / deletion behavior

`features/cascadedeletion/CascadeDeletionService` centralizes safe deletions so
join-table rows and dependent children are removed explicitly (Hibernate
cascade is not relied upon for cross-entity cleanup).

- **Schedule tree** (`purgeScheduleTree`): deletes `ClassSession` and
  `PreAllocation` rows for the schedule and **all descendant schedules**
  (recursively via `parentScheduleId`), then deletes the schedule rows.
- **Pre-allocations** can be purged by batch / subject / teacher / room /
  timeslot / department.
- **Timeslot detach** (`detachTimeslot`): removes the timeslot from
  teacher/room availability and event join tables.
- **Event detach**: rooms/teachers removed from events before their own
  deletion.

### `Batch.homeRoom` FK handling
`Batch.homeRoom` is a plain `@ManyToOne` with **no** cascade/`@OnDelete`. To avoid
a foreign-key violation when a `Room` (or its `Building`) is deleted, the
deleters null the FK first:

- `RoomServiceImpl.delete` calls `BatchRepository.clearHomeRoomByRoomId(id)`
  (`UPDATE Batch b SET b.homeRoom = null WHERE b.homeRoom.id = :roomId`).
- `BuildingServiceImpl.delete` nulls `homeRoom` for every room in the building
  (`clearHomeRoomByBuildingId`).

---

## Join / collection tables (auto-created by mappings)

| Table | Owner → Target |
| --- | --- |
| `department_buildings` | Department → Building |
| `room_availability` | Room → Timeslot |
| `batch_subjects` | Batch → Subject |
| `class_section_subjects` | ClassSection → Subject |
| `teacher_subjects` | Teacher → Subject |
| `teacher_availability` | Teacher → Timeslot |
| `teacher_preferred_buildings` | Teacher → Building |
| `event_affected_rooms` | Event → Room |
| `event_affected_teachers` | Event → Teacher |
| `event_affected_timeslots` | Event → Timeslot |
| `batch_working_days` | Batch → SchoolDay (element collection) |
| `schedule_blocked_days` | Schedule → SchoolDay (element collection) |
| `config_break_indices` | UniversityConfig → Integer (element collection) |
| `config_working_days` | UniversityConfig → SchoolDay (element collection) |
