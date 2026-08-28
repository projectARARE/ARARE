# ARARE Architecture

ARARE (Adaptive Real-Time Analysis and Re-evaluation Engine) is a university
timetable scheduling system. It takes master data (institutes, departments,
buildings, rooms, batches, teachers, subjects, timeslots, …) and produces a
conflict-free weekly timetable using the Timefold constraint solver. The
backend is a Spring Boot service; the frontend is a React + TypeScript single-page
app.

> Audience: backend developers who need to understand how the system fits together
> before changing code. Read `DOMAIN_MODEL.md` for the data shape and `API.md` for
> the HTTP surface.

---

## 1. Tech stack

| Layer | Technology |
| --- | --- |
| Language / runtime | Java 21, Spring Boot 3.3.0 |
| Constraint solver | Timefold Solver 1.14.0 (`timefold-solver-spring-boot-starter`, `timefold-solver-core`) — the community successor to OptaPlanner |
| Persistence | Spring Data JPA (Hibernate), PostgreSQL in production, H2 in tests |
| Migrations | Flyway (SQL scripts in `src/main/resources/db/migration`, `V1`…`V11`) |
| DTO mapping | **Hand-written** mapping in service classes. A `mapstruct` dependency is declared in `pom.xml` and its annotation processor is registered, but **no `@Mapper` interface exists** in the codebase — mapping is done manually (see the many `toResponse(...)` methods). |
| Scheduling / async | Spring `@Async` on a dedicated `ThreadPoolTaskExecutor` (`solveTaskExecutor`) |
| Excel export | Apache POI 5.2.5 (`poi-ooxml`) |
| PDF export | OpenPDF 2.0.3 (`com.github.librepdf:openpdf`) |
| CSV import/export | Hand-written CSV utilities (`features/dataimport`) |
| Frontend | React 18 + TypeScript 5 + Vite 5, TanStack Query, React Router, Tailwind, Zod |
| Build | Maven (backend), npm/Vite (frontend) |
| Lombok | Used throughout `@Entity`/`@Service`/`@Controller` classes |

---

## 2. Layered backend

```
HTTP request
   │
   ▼
@RestController  (controllers: /api/v1/**)
   │  validate @RequestBody (@Valid), delegate to Service
   ▼
@Service  (@Transactional business logic, orchestration)
   │  uses repositories, external solver, mapping to DTOs
   ▼
JpaRepository  (thin, query-method only)
   │
   ▼
@Entity  (JPA, extends BaseEntity)  ⇄  PostgreSQL
```

- **Controllers** (`features/**/*Controller`) expose REST only. They do not
  contain business logic; they validate the request body and call the service.
- **Services** (`*ServiceImpl`) own a single responsibility, are annotated
  `@Transactional`, and wrap each high-level operation in one transaction unless
  noted otherwise (the solve pipeline deliberately breaks this rule — see §4).
- **Repositories** are Spring Data JPA interfaces; they only add derived queries
  or a few `@Query` methods. No business logic lives here.
- **Entities** extend `common/BaseEntity` (see §3).

### `common/BaseEntity`
`src/main/java/com/arare/common/BaseEntity.java`

All JPA entities (except `ClassSession`, which uses `@PlanningId`) extend
`BaseEntity` (`@MappedSuperclass`):

- `Long id` — `@GeneratedValue(IDENTITY)`, the primary key.
- `LocalDateTime createdAt` / `updatedAt` — populated by
  `AuditingEntityListener` (`@CreatedDate` / `@LastModifiedDate`, enabled via
  `config/JpaConfig` `@EnableJpaAuditing`).
- `Long version` — `@Version` optimistic locking.
- `equals`/`hashCode` are **ID-based**: two entities are equal only if both have
  a non-null `id` that matches. Two unpersisted entities are never equal. This
  matters because Timefold's constraint joiners use `Objects.equals` and must
  match Hibernate proxies against eagerly-loaded instances of the same row.

`ClassSession` does **not** extend `BaseEntity`; it declares its own
`@PlanningId @Id Long id` to avoid Timefold serialization problems with the
`@MappedSuperclass`.

### `exception/GlobalExceptionHandler`
`src/main/java/com/arare/exception/GlobalExceptionHandler.java`

A single `@RestControllerAdvice` converts thrown exceptions into **RFC 9457
`ProblemDetail`** JSON responses. Application (safe) messages are returned to the
client; framework/unexpected exceptions are logged server-side and reported with
a generic message (never a stack trace).

| Exception | HTTP |
| --- | --- |
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `ResourceConflictException` | 409 |
| `ResourceBusyException` | 409 |
| `MethodArgumentNotValidException` / `ConstraintViolationException` / `IllegalArgumentException` / missing-param / type-mismatch | 400 |
| `InfeasibleScheduleException` | 422 Unprocessable Entity |
| `DataIntegrityViolationException` (FK/unique/not-null) | 409 |
| `ObjectOptimisticLockingFailureException` | 409 |
| `IllegalStateException` and any other `Exception` | 500 |

All `ProblemDetail` bodies carry a `type` URI (e.g. `/errors/not-found`) and a
human-readable `detail`.

### Exception hierarchy
`src/main/java/com/arare/exception/`

- `ResourceNotFoundException` — entity id not found (→ 404).
- `DuplicateResourceException` — unique violation on create/update (→ 409).
- `ResourceConflictException` — operation not permitted in current state (→ 409),
  e.g. cancelling a job that is no longer cancellable.
- `ResourceBusyException` — resource in use by an in-flight process (→ 409), e.g.
  deleting a schedule while a solve job is QUEUED/RUNNING.
- `InfeasibleScheduleException` — no feasible solution / pre-flight feasibility
  check failed (→ 422).

---

## 3. Concurrency & thread-safety

- **Optimistic locking**: every `BaseEntity` has a `@Version`. Concurrent edits
  surface as `ObjectOptimisticLockingFailureException` → 409.
- **`open-in-view=false`** (`spring.jpa.open-in-view=false`): a persistence
  context is only open for the duration of the `@Transactional` method, never
  around view rendering. Services must fetch everything they need inside the
  transaction.
- **Solve pipeline isolation**: the long-running solver does **not** hold a
  transaction (see §4), so it cannot block DB connections or collide with web
  threads at the row level.
- **Solve-job state machine** uses guarded bulk `UPDATE` statements
  (`SolveJobRepository.transitionTerminal`) whose `WHERE` clause includes the
  current status. These are plain `@Modifying` updates and **do not bump
  `@Version`**, so a concurrent `cancel` and a finishing `run` race via the
  database rather than via optimistic-lock exceptions. Exactly one of them wins
  the row update; the loser persists nothing.
- **`ActiveSolverRegistry`** keeps the live `Solver` instance keyed by
  `problemId` (a UUID written into the job row) so `cancel` can call
  `solver.terminateEarly()` on the in-memory solver.

---

## 4. Asynchronous solve pipeline

Schedule generation (and disruption partial-resolution) is decoupled from the
HTTP request that triggered it. The API returns a `SolveJob` row immediately and
the worker runs on a separate thread.

### Lifecycle

1. **Submit (caller's transaction).** `ScheduleServiceImpl.generate()` runs in a
   `@Transactional`. It runs a feasibility pre-check
   (`FeasibilityCheckService.check`); if infeasible it throws
   `InfeasibleScheduleException` (→ 422) *before* writing anything. Otherwise it:
   - creates the `Schedule` (status `DRAFT`) and saves it,
   - calls `ensureNoActiveJobForSchedule` (rejects if a QUEUED/RUNNING job
     already exists → `ResourceBusyException`),
   - persists the wizard's pre-allocations,
   - calls `SolveJobService.submitGenerate(...)`, which inserts a `SolveJob`
     (`status = QUEUED`) and **schedules the worker in an `afterCommit`
     hook** (`TransactionSynchronizationManager.registerSynchronization` →
     `runner.run`). The hook guarantees the worker thread only starts *after* the
     job row is committed, so it can always read it.

2. **Run (`SolveJobRunner.run`, `@Async("solveTaskExecutor")`).**
   - It loads the job; if `CANCELLED` it returns immediately.
   - Guarded `transitionTerminal(QUEUED → RUNNING)` writing the `problemId`. If
     `0` rows change, the job was already cancelled → return, persist nothing.
   - **Build the problem** inside a *read-only* short transaction
     (`transactionTemplate.execute` → `TimetableProblemBuilder.build`). This is
     the only DB read the worker does.
   - **Solve with no DB connection open.** The solver runs entirely in memory
     (`solver.solve(problem)`). The `ActiveSolverRegistry` holds the `Solver`
     so it can be terminated. A `BestScoreListener` writes progress
     (`updateBestScoreIfActive`, guarded to QUEUED/RUNNING) — telemetry only,
     failures swallowed.
   - **Persist atomically.** Inside one write transaction it first attempts the
     guarded `transitionTerminal(QUEUED|RUNNING → SUCCEEDED)`. If that updates `0`
     rows (a concurrent `cancel` won), it **does not persist the solution** and
     returns. Otherwise it calls `SolutionPersister.persist(schedule, solution)`
     in the *same* transaction and commits. This closes the cancel/persist race.
   - On any exception, `markFailed` does a guarded
     `transitionTerminal(QUEUED|RUNNING → FAILED)`; a concurrent cancel still
     wins and keeps the `CANCELLED` status.
   - `finally` unregisters the solver from `ActiveSolverRegistry`.

3. **Cancel.** `SolveJobService.cancel` calls `solver.terminateEarly()` (if
   RUNNING and a live solver exists) and then a guarded
   `transitionTerminal(QUEUED|RUNNING → CANCELLED)`. If `0` rows change the job
   already finished → `ResourceConflictException`.

4. **Recovery on startup.** `SolveJobRecoverySweeper` listens for
   `ApplicationReadyEvent` and marks any leftover `QUEUED`/`RUNNING` jobs
   `FAILED` (so they cannot block schedule deletion forever).

### Thread pool (`AsyncConfig`)
`solveTaskExecutor` — `ThreadPoolTaskExecutor`:

- core pool size **2**, max pool size **4**, queue capacity **100**
- `CallerRunsPolicy` (the caller runs the task when the queue is full)
- graceful shutdown: `waitForTasksToCompleteOnShutdown = true`,
  `awaitTerminationSeconds = 60`
- thread name prefix `solve-`

### Solver termination
`timefold.solver.termination.spent-limit = 30s`
(`application.properties`); the per-request `solvingTimeSeconds` overrides it via
a `SolverConfigOverride`. `environment-mode = REPRODUCIBLE`.

---

## 5. CORS

`config/WebConfig` registers a `WebMvcConfigurer` that maps `/api/**`:

- `allowedOriginPatterns` = `${arare.cors.allowed-origins:http://localhost:5173}`
  (comma-separated, read via `@Value`).
- methods: GET, POST, PUT, PATCH, DELETE, OPTIONS.
- `allowCredentials(true)`, `maxAge(3600)`.

Because credentials are allowed, the origin must be an explicit list — never `*`.
Set `ARARE_CORS_ORIGINS` to the real frontend origin in deployment.

---

## 6. Authentication & deployment model

There is **no authentication/authorization layer by design**. ARARE is intended
to run on a university's own infrastructure on a trusted local network (or
single-tenant cloud) where the operator is trusted. `DOCUMENTATION.md`/deployment
guidance therefore focuses on network isolation and CORS rather than login.

The model is **single-deployment, not multi-tenant**: one database, one
university's data, one active config row (`UniversityConfig`). `Schedule.scope`
(`DEPARTMENT` / `INSTITUTE` / `UNIVERSITY`) and `Schedule.instituteId` partition
schedules within that single deployment but there is no per-request tenant
resolution.

---

## 7. Request-flow diagrams

### Generate a full schedule
```
POST /api/v1/schedules/generate
   │ ScheduleRequest{name, scope, batchIds, teacherIds, roomIds,
   │                  solvingTimeSeconds, preAllocations, ...}
   ▼
ScheduleServiceImpl.generate  (@Transactional)
   ├─ FeasibilityCheckService.check  ──infeasible?──▶ 422
   ├─ repo.save(Schedule DRAFT)
   ├─ ensureNoActiveJobForSchedule   ──busy?──▶ 409
   ├─ preAllocationService.createAll
   └─ SolveJobService.submitGenerate → SolveJob QUEUED
            │ (afterCommit hook)
            ▼
       SolveJobRunner.run (@Async solveTaskExecutor)
            ├─ QUEUED→RUNNING (guarded)
            ├─ build problem (read-only tx)
            ├─ solve in-memory (no DB)
            └─ QUEUED/RUNNING→SUCCEEDED + persist (one tx)  [cancel wins → skip]
   ◀── 202 Accepted + SolveJobResponse (id, status=QUEUED)
poll GET /api/v1/solve-jobs/{id}
```

### Manual edit of a session
```
PATCH /api/v1/sessions/{id}   SessionAssignmentRequest{teacherId, roomId, timeslotId, locked, clear*...}
   ▼ ClassSessionServiceImpl.updateAssignment  (@Transactional)
   ◀── 200 ClassSessionResponse
```

### Disruption / partial re-solve
```
POST /api/v1/schedules/{id}/disruption/preview   DisruptionRequest
   ▼ DisruptionService.previewImpact  (analytical, no solve)
   ◀── 200 DisruptionResponse

POST /api/v1/schedules/{id}/disruption/apply     DisruptionRequest
   ▼ DisruptionService.applyDisruption → submitPartialResolve(impactedSessionIds, disruptionFacts)
   ▼ SolveJob QUEUED → SolveJobRunner (re-solves only impacted sessions, others pinned)
   ◀── 202 Accepted + SolveJobResponse
```
`POST /api/v1/schedules/{id}/partial-resolve` does the same without an
`Event`/disruption, taking explicit `impactedSessionIds`.

### CSV / ZIP import
```
POST /api/v1/import/csv/{entityType}   {csvContent, dryRun}      → upsert one entity kind
POST /api/v1/import/zip   (multipart)                            → full relational import (dependency order)
GET  /api/v1/import/export/zip | /export/csv/{entityType} | /template/csv/{entityType} | /template/zip | /order
```

### Export
```
GET /api/v1/schedules/{id}/export/csv        → text/csv
GET /api/v1/schedules/{id}/export/pdf?view&entityId   → application/pdf (OpenPDF)
GET /api/v1/schedules/{id}/export/excel?view&entityId → xlsx (POI)
POST /api/v1/export/excel   {sheetName, headers, rows} → generic xlsx (any master-data grid)
```

---

## 8. Configuration classes (separation of concerns)

| Class | Responsibility |
| --- | --- |
| `config/AsyncConfig` | `solveTaskExecutor` + `TransactionTemplate` bean |
| `config/JpaConfig` | `@EnableJpaAuditing`; custom `Flyway` bean (opt-in `auto-create-db`) |
| `config/WebConfig` | CORS mapping for `/api/**` |
| `ArareApplication` | Spring Boot entry point |

Business logic lives in `*ServiceImpl`; cross-cutting orchestration
(`SolveJobService`, `CascadeDeletionService`, `DisruptionService`) is kept
separate from controllers and entities.
