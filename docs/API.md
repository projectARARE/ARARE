# ARARE REST API Reference

Base path: `/api/v1`. All requests and responses are `application/json` unless
stated otherwise. Errors follow **RFC 9457 `ProblemDetail`** (see
`ARCHITECTURE.md` §2). Many endpoints that start async work return `202 Accepted`
with a `SolveJobResponse`; poll `GET /api/v1/solve-jobs/{id}` for status.

Conventions:
- Path params: `{id}` = entity id (`Long`).
- All list endpoints return JSON arrays.
- Create returns `201 Created`; delete returns `204 No Content`.

## Error codes (all resources)

| HTTP | Trigger |
| --- | --- |
| 400 | Validation failure (`@Valid`/`@NotNull`/type mismatch/malformed body) |
| 404 | `ResourceNotFoundException` — id not found |
| 409 | `DuplicateResourceException` / `ResourceConflictException` / `ResourceBusyException` / DB constraint violation / optimistic-lock |
| 422 | `InfeasibleScheduleException` — pre-flight feasibility check failed or no feasible solution |
| 500 | Unexpected server error |

---

## Institutes — `/api/v1/institutes`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/institutes` | Create | `InstituteRequest{name, code, description}` |
| PUT | `/institutes/{id}` | Update | `InstituteRequest` |
| GET | `/institutes/{id}` | Get by id | — |
| GET | `/institutes` | List all | — |
| DELETE | `/institutes/{id}` | Delete | — |

`code` must match `^[A-Za-z0-9_-]+$` and is unique.

## Departments — `/api/v1/departments`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/departments` | Create | `DepartmentRequest{name, code, instituteId, buildingIds[]}` |
| PUT | `/departments/{id}` | Update | `DepartmentRequest` |
| GET | `/departments/{id}` | Get by id | — |
| GET | `/departments` | List all | — |
| DELETE | `/departments/{id}` | Delete | — |

## Buildings — `/api/v1/buildings`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/buildings` | Create | `BuildingRequest{name, location}` |
| PUT | `/buildings/{id}` | Update | `BuildingRequest` |
| GET | `/buildings/{id}` | Get by id | — |
| GET | `/buildings` | List all | — |
| GET | `/buildings/{buildingId}` | List by building | — |
| DELETE | `/buildings/{id}` | Delete | — (nulls `Batch.homeRoom` FKs first) |

## Rooms — `/api/v1/rooms`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/rooms` | Create | `RoomRequest{buildingId, roomNumber, type, labSubtype?, capacity, availableTimeslotIds[]}` |
| PUT | `/rooms/{id}` | Update | `RoomRequest` |
| GET | `/rooms/{id}` | Get by id | — |
| GET | `/rooms` | List all | — |
| GET | `/rooms/building/{buildingId}` | List by building | — |
| DELETE | `/rooms/{id}` | Delete | — (nulls `Batch.homeRoom` FKs first) |

`type=LAB` requires `labSubtype`. `availableTimeslotIds` empty = available all CLASS slots.

## Batches — `/api/v1/batches`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/batches` | Create | `BatchRequest{departmentId, year, section, studentCount, workingDays[], preferredFreeDay?, homeRoomId?, subjectIds[]}` |
| PUT | `/batches/{id}` | Update | `BatchRequest` |
| GET | `/batches/{id}` | Get by id | — |
| GET | `/batches` | List all | — |
| GET | `/batches/department/{departmentId}` | List by department | — |
| DELETE | `/batches/{id}` | Delete | — |

Unique on (`department_id`, `year`, `section`).

## Class Sections — `/api/v1/class-sections`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/class-sections` | Create | `ClassSectionRequest{batchId, label, size, subjectIds[]}` |
| POST | `/class-sections/bulk` | Bulk-generate sections | `ClassSectionBulkRequest{batchId, prefix, count, size}` |
| PUT | `/class-sections/{id}` | Update | `ClassSectionRequest` |
| GET | `/class-sections` | List all | — |
| GET | `/class-sections/{id}` | Get by id | — |
| GET | `/class-sections/batch/{batchId}` | List by batch | — |
| DELETE | `/class-sections/{id}` | Delete | — |

## Teachers — `/api/v1/teachers`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/teachers` | Create | `TeacherRequest{employeeId?, name, subjectIds[], availableTimeslotIds[], preferredBuildingIds[], maxDailyHours, maxWeeklyHours, maxConsecutiveClasses, movementPenalty, preferredFreeDay?}` |
| PUT | `/teachers/{id}` | Update | `TeacherRequest` |
| GET | `/teachers/{id}` | Get by id | — |
| GET | `/teachers` | List all | — |
| DELETE | `/teachers/{id}` | Delete | — |

`employeeId` is unique (natural key for CSV).

## Subjects — `/api/v1/subjects`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/subjects` | Create | `SubjectRequest{name, code, departmentId?, weeklyHours, chunkHours, roomTypeRequired, labSubtypeRequired?, isLab, requiresTeacher, requiresRoom, minGapBetweenSessions, maxSessionsPerDay}` |
| PUT | `/subjects/{id}` | Update | `SubjectRequest` |
| GET | `/subjects/{id}` | Get by id | — |
| GET | `/subjects` | List all | — |
| GET | `/subjects/department/{departmentId}` | List by department | — |
| DELETE | `/subjects/{id}` | Delete | — |

Invariants enforced: `weeklyHours` divisible by `chunkHours`; `isLab` ⇔ `roomTypeRequired=LAB`.

## Subject Offerings — `/api/v1/subject-offerings`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/subject-offerings` | Create | `SubjectOfferingRequest{subjectId, batchId?, sectionId?, weeklyHours?, elective}` |
| PUT | `/subject-offerings/{id}` | Update | `SubjectOfferingRequest` |
| GET | `/subject-offerings` | List all | — |
| GET | `/subject-offerings/{id}` | Get by id | — |
| GET | `/subject-offerings/batch/{batchId}` | List by batch | — |
| GET | `/subject-offerings/section/{sectionId}` | List by section | — |
| GET | `/subject-offerings/subject/{subjectId}` | List by subject | — |
| DELETE | `/subject-offerings/{id}` | Delete | — |

Exactly one of `batchId`/`sectionId` required.

## Teacher Assignments — `/api/v1/teacher-assignments`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/teacher-assignments` | Create | `TeacherAssignmentRequest{teacherId, subjectId, batchId?, sectionId?, weeklyHours?, priority?, notes?}` |
| PUT | `/teacher-assignments/{id}` | Update | `TeacherAssignmentRequest` |
| GET | `/teacher-assignments` | List all | — |
| GET | `/teacher-assignments/{id}` | Get by id | — |
| GET | `/teacher-assignments/teacher/{teacherId}` | List by teacher | — |
| GET | `/teacher-assignments/batch/{batchId}` | List by batch | — |
| GET | `/teacher-assignments/subject/{subjectId}` | List by subject | — |
| DELETE | `/teacher-assignments/{id}` | Delete | — |

Exactly one of `batchId`/`sectionId` required. Hard constraint for the solver.

## Timeslots — `/api/v1/timeslots`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/timeslots` | Create | `TimeslotRequest{day, startTime, endTime, slotNumber?, type}` |
| PUT | `/timeslots/{id}` | Update | `TimeslotRequest` |
| GET | `/timeslots/{id}` | Get by id | — |
| GET | `/timeslots` | List all | — |
| DELETE | `/timeslots/{id}` | Delete | — (detaches from teacher/room/event join tables) |

Unique on (`day`, `start_time`, `end_time`); `endTime` must be after `startTime`.

## Sessions (ClassSession) — `/api/v1/sessions`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| GET | `/sessions/schedule/{scheduleId}` | Sessions of a schedule | — |
| GET | `/sessions/schedule/{scheduleId}/batch/{batchId}` | Sessions of schedule+batch | — |
| GET | `/sessions/schedule/{scheduleId}/teacher/{teacherId}` | Sessions of schedule+teacher | — |
| PATCH | `/sessions/{id}` | Reassign teacher/room/timeslot/lock | `SessionAssignmentRequest{teacherId?, roomId?, timeslotId?, locked?, clearTeacher?, clearRoom?, clearTimeslot?}` |
| POST | `/sessions` | Manually create a session | `SessionCreateRequest{scheduleId, subjectId, batchId?, sectionId?, teacherId?, roomId?, timeslotId?, duration?, locked?}` |
| DELETE | `/sessions/{id}` | Delete | — |

`PATCH` nulls the field when the matching `clear*` flag is true (or id set null).

## Events (disruptions) — `/api/v1/events`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/events` | Create event | `EventRequest{title, type, startDate, endDate, description?, affectedRoomIds[], affectedTeacherIds[], affectedTimeslotIds[]}` |
| PUT | `/events/{id}` | Update | `EventRequest` |
| GET | `/events/{id}` | Get by id | — |
| GET | `/events` | List all | — |
| POST | `/events/{eventId}/apply/{scheduleId}` | Apply event → async partial re-solve | — → `202 SolveJobResponse` |
| DELETE | `/events/{id}` | Delete | — |

## Academic Terms — `/api/v1/academic-terms`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| GET | `/academic-terms` | List all | — |
| GET | `/academic-terms/{id}` | Get by id | — |
| POST | `/academic-terms` | Create | `AcademicTermRequest{name, academicYear?, startDate, endDate, examPeriodStart?, examPeriodEnd?, status?, description?}` |
| PUT | `/academic-terms/{id}` | Update | `AcademicTermRequest` |
| DELETE | `/academic-terms/{id}` | Delete | — |

## Schedules — `/api/v1/schedules`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/schedules/generate` | Generate (async) | `ScheduleRequest{name, scope, parentScheduleId?, departmentId?, instituteId?, batchIds[], teacherIds[], roomIds[], solvingTimeSeconds?, blockedDays[], preAllocations[]}` → `202 SolveJobResponse` |
| GET | `/schedules/{id}` | Get by id | — |
| GET | `/schedules` | List all | — |
| POST | `/schedules/{id}/activate` | Activate (archives other active in scope) | — |
| POST | `/schedules/{id}/archive` | Archive | — |
| POST | `/schedules/{id}/partial-resolve` | Re-solve impacted sessions (async) | `PartialResolveRequest{impactedSessionIds[]}` → `202 SolveJobResponse` |
| GET | `/schedules/{id}/score-explanation` | Score breakdown | — → `ScoreExplanationResponse` |
| GET | `/schedules/{id}/explanation` | Score explanation text | — |
| GET | `/schedules/{id}/sessions/{sessionId}/suggestions?limit=4` | Conflict fix suggestions | — |
| GET | `/schedules/{id}/sessions` | Sessions of schedule | — |
| DELETE | `/schedules/{id}` | Delete (purges tree) | — (rejects if active job → 409) |
| POST | `/schedules/{id}/disruption/preview` | Preview impact (no solve) | `DisruptionRequest` → `DisruptionResponse` |
| POST | `/schedules/{id}/disruption/apply` | Apply disruption (async partial resolve) | `DisruptionRequest` → `202 SolveJobResponse` |
| GET | `/schedules/{id}/export/csv` | Export CSV | — (`text/csv`) |
| GET | `/schedules/{id}/export/pdf?view=ALL&entityId=` | Export PDF | — (`application/pdf`) |
| GET | `/schedules/{id}/export/excel?view=ALL&entityId=` | Export Excel | — (`xlsx`) |
| POST | `/schedules/feasibility-check` | Pre-flight feasibility (no solve) | `ScheduleRequest` → `FeasibilityCheckResult` |

`ScheduleRequest.preAllocations` items are `PreAllocationSpec{batchId, subjectId, teacherId?, roomId?, timeslotId?}` (timeslotId optional → only teacher/room pinned).
Generate runs a feasibility check first; if infeasible → `422`.

## Pre-Allocations — `/api/v1/pre-allocations`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/pre-allocations` | Create | `PreAllocationRequest{scheduleId, batchId, subjectId, teacherId?, roomId?, timeslotId?, locked}` |
| GET | `/pre-allocations/{id}` | Get by id | — |
| GET | `/pre-allocations/schedule/{scheduleId}` | List by schedule | — |
| DELETE | `/pre-allocations/{id}` | Delete | — |

Unique on (`schedule_id`, `batch_id`, `subject_id`, `timeslot_id`).

## Solve Jobs — `/api/v1/solve-jobs`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| GET | `/solve-jobs?status=` | List (optionally by status) | — |
| GET | `/solve-jobs/schedule/{scheduleId}` | List by schedule | — |
| GET | `/solve-jobs/{id}` | Get status | — |
| POST | `/solve-jobs/{id}/cancel` | Cancel (terminates live solver) | — → `409` if not cancellable |

`SolveJobResponse{id, jobType, scheduleId, status, score, bestScore, errorMessage, elapsedMillis, createdAt, startedAt, finishedAt}`.

## Disruptions (impact analysis) — see Events + Schedules
The impact engine is driven by `DisruptionRequest{type, affectedEntityId?, date?, description?}` and `DisruptionType` (see `DOMAIN_MODEL.md`). Endpoints:
- `POST /schedules/{id}/disruption/preview`
- `POST /schedules/{id}/disruption/apply`
- `POST /events/{eventId}/apply/{scheduleId}`

## Data Import / Export — `/api/v1/import`
| Method | Path | Purpose | Body / Type |
| --- | --- | --- | --- |
| POST | `/import/csv/{entityType}` | Upsert one entity kind | `CsvImportRequest{csvContent, dryRun}` (JSON) |
| POST | `/import/zip` | Full relational import (dependency order) | `multipart/form-data` file + `dryRun` param |
| GET | `/import/export/zip` | Export all as ZIP | — (`application/zip`) |
| GET | `/import/export/csv/{entityType}` | Export one entity as CSV | — (`text/csv`) |
| GET | `/import/template/csv/{entityType}` | Blank template CSV | — (`text/csv`) |
| GET | `/import/template/zip` | All templates ZIP | — (`application/zip`) |
| GET | `/import/order` | Canonical import order | — (`List<ImportOrderStep>`) |

`{entityType}` is a `CsvEntityType` name (e.g. `institute`, `department`,
`building`, `room`, `batch`, `class-section`, `teacher`, `subject`,
`subject-offering`, `teacher-assignment`, `timeslot`, …).

## Generic Spreadsheet Export — `/api/v1/export`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/export/excel` | Build an `.xlsx` from arbitrary rows | `ExportRequest{sheetName, headers[], rows[][]}` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

Used by the frontend to export any master-data grid.

## University Config — `/api/v1/university-config`
| Method | Path | Purpose | Body |
| --- | --- | --- | --- |
| POST | `/university-config` | Create or replace active config | `UniversityConfigRequest{daysPerWeek, timeslotsPerDay, maxClassesPerDay, breakSlotIndices[], workingDays[]}` |
| GET | `/university-config` | Get active config | — |
| GET | `/university-config/diagnostics` | Diagnostics (coverage/feasibility hints) | — → `UniversityConfigDiagnosticsResponse` |

Only one active config row is expected.
