# ARARE API Contract & Enforcement Audit

Date: 2026-08-10. Verification pass over the full frontend/backend contract —
read-only mapping plus fixes that each carry their own commit.

Scope notes:

- Every HTTP call site in the frontend lives in `src/services/api.ts` (single
  axios instance, `baseURL: '/api/v1'`). A grep for `fetch(`, `axios`,
  `XMLHttpRequest`, and direct `api.*` calls across `src/pages`,
  `src/components`, and `src/hooks` found **zero call sites outside api.ts**
  — the P10 consolidation holds.
- Inline full-URL construction survives only in `scheduleApi.getTeacherIcalUrl` /
  `getBatchIcalUrl` (`/api/v1/schedules/ical/...`). These are consumed by
  `CalendarPortal.tsx` as window/href targets; they resolve under the Vite dev
  proxy (`vite.config.ts` forwards `/api` → `http://localhost:8080`) and under
  any same-origin production proxy, so no base-URL drift.
- Error handling: `axios` interceptor maps `ProblemDetail.detail` →
  `message` → generic `err.message`. All backend error responses are
  RFC-9457 `ProblemDetail` (see `GlobalExceptionHandler`). For **blob**
  downloads (CSV / iCal / ZIP) `err.response.data` is a Blob, not JSON, so the
  interceptor falls back to the generic `err.message` string — noted per row.

## Part A — Endpoint ↔ caller table

Legend: REQ = request DTO field-match, RES = response DTO field-match,
ERR = error-status coverage in the frontend. "FE dead" = defined in api.ts
but never referenced by any page/component/hook.

| Endpoint (backend) | Frontend caller(s) | REQ | RES | ERR | Notes |
|---|---|---|---|---|---|
| `GET /buildings` | `buildingApi.getAll` (Buildings, Departments, Dashboard) | — | OK | n/a | — |
| `POST /buildings` | `buildingApi.create` (Buildings) | OK | OK | 400/404/409→toast | name `@NotBlank` |
| `PUT /buildings/{id}` | `buildingApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /buildings/{id}` | `buildingApi.delete` | — | — | 400/404/409→toast | FK protection via 409 handler |
| `GET /buildings/{id}` | FE dead (`getById`) | — | OK | — | orphaned client side |
| `GET /departments` | `departmentApi.getAll` (Departments, Batches, Subjects, ScheduleGenerator) | — | OK | — | — |
| `POST /departments` | `departmentApi.create` | OK | OK | 400/404/409→toast | name+code `@NotBlank` |
| `PUT /departments/{id}` | `departmentApi.update` | OK | OK | 400/404/409→toast | unknown buildingIds → 400 (earlier fix) |
| `DELETE /departments/{id}` | `departmentApi.delete` | — | — | 400/404/409→toast | — |
| `GET /departments/{id}` | FE dead | — | OK | — | — |
| `GET /rooms` | `roomApi.getAll` (Rooms, Events, TimetableViewer, Analytics) | — | OK | — | — |
| `POST /rooms` | `roomApi.create` | OK | OK | 400/404/409→toast | `capacity @Min(1)` |
| `PUT /rooms/{id}` | `roomApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /rooms/{id}` | `roomApi.delete` | — | — | 400/404/409→toast | — |
| `GET /rooms/{id}` | FE dead | — | OK | — | — |
| `GET /rooms/building/{buildingId}` | **no backend caller** | — | OK | — | orphaned endpoint |
| `GET /teachers` | `teacherApi.getAll` (Teachers, Dashboard, Events, CalendarPortal, ScheduleGenerator, TimetableViewer) | — | OK | — | — |
| `POST /teachers` | `teacherApi.create` | OK | OK | 400/404/409→toast | name `@NotBlank`, hours `@Min(1)` |
| `PUT /teachers/{id}` | `teacherApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /teachers/{id}` | `teacherApi.delete` | — | — | 400/404/409→toast | — |
| `GET /teachers/{id}` | FE dead | — | OK | — | — |
| `GET /subjects` | `subjectApi.getAll` (Subjects, Teachers, Dashboard, ScheduleGenerator) | — | OK | — | — |
| `POST /subjects` | `subjectApi.create` | OK | OK | 400/404/409→toast | name+code `@NotBlank`, dept `@NotNull`, `roomTypeRequired @NotNull` — TS marks it optional; omitting it → 400 (server-strict, safe direction) |
| `PUT /subjects/{id}` | `subjectApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /subjects/{id}` | `subjectApi.delete` | — | — | 400/404/409→toast | — |
| `GET /subjects/{id}` | FE dead | — | OK | — | — |
| `GET /subjects/department/{departmentId}` | **no backend caller** | — | OK | — | orphaned endpoint |
| `GET /batches` | `batchApi.getAll` (Batches, ClassSections, CalendarPortal, ScheduleGenerator, TimetableViewer) | — | OK | — | — |
| `POST /batches` | `batchApi.create` | OK | OK | 400/404/409→toast | — |
| `PUT /batches/{id}` | `batchApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /batches/{id}` | `batchApi.delete` | — | — | 400/404/409→toast | — |
| `GET /batches/{id}` | FE dead | — | OK | — | — |
| `GET /batches/department/{departmentId}` | **no backend caller** | — | OK | — | orphaned endpoint |
| `GET /class-sections` | `classSectionApi.getAll` | — | OK | — | — |
| `GET /class-sections/{id}` | FE dead | — | OK | — | — |
| `GET /class-sections/batch/{batchId}` | FE dead (`getByBatch`) | — | OK | — | — |
| `POST /class-sections` | `classSectionApi.create` | OK | OK | 400/404/409→toast | — |
| `PUT /class-sections/{id}` | `classSectionApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /class-sections/{id}` | `classSectionApi.delete` | — | — | 400/404/409→toast | — |
| `GET /timeslots` | `timeslotApi.getAll` (Timeslots, Rooms, Teachers, UniversityConfig, ScheduleGenerator, TimetableViewer) | — | OK | — | `startTime/endTime` HH:mm via `@JsonFormat` (P7) |
| `POST /timeslots` | `timeslotApi.create` | OK | OK | 400/404/409→toast | server validates start<end, overlap, slotNumber (see Part B) |
| `PUT /timeslots/{id}` | `timeslotApi.update` | OK | OK | 400/404/409→toast | same rules |
| `DELETE /timeslots/{id}` | `timeslotApi.delete` | — | — | 400/404/409→toast | detaches sessions/joins first |
| `GET /timeslots/{id}` | FE dead | — | OK | — | — |
| `GET /university-config` | `universityConfigApi.get` (UniversityConfig) | — | OK | 404→handled | 404 when no active config yet (page treats as empty state) |
| `POST /university-config` | `universityConfigApi.save` | OK | OK | 400/404/409→toast | TS also sends `active`; backend record has no such field and always saves `active=true` (accepted-and-ignored, matches A5 resolution) |
| `GET /university-config/diagnostics` | **no backend caller** | — | OK | — | orphaned endpoint |
| `GET /schedules` | `scheduleApi.getAll` (ScheduleHistory, Dashboard, Analytics/WhatIf) | — | OK | — | — |
| `GET /schedules/{id}` | `scheduleApi.getById` (ScheduleHistory, TimetableViewer) | — | OK | — | — |
| `POST /schedules/generate` | `scheduleApi.generate` (ScheduleGenerator) | OK | OK | 400/404/409/422→toast | 202 + SolveJobResponse; 422 IllegalState when infeasible |
| `POST /schedules/{id}/partial-resolve` | FE dead (`partialResolve`) — backend path used indirectly by disruption apply + event apply | OK | OK | — | orphaned client side |
| `GET /schedules/{id}/score-explanation` | `scheduleApi.getScoreExplanation` (TimetableViewer) | — | OK | — | — |
| `GET /schedules/{id}/explanation` | `scheduleApi.getExplanation` (TimetableViewer) | — | OK | — | — |
| `GET /schedules/{id}/sessions` | `scheduleApi.getSessions` (TimetableViewer, DisruptionHandling) | — | OK | — | — |
| `GET /schedules/{id}/sessions/{sessionId}/suggestions` | FE dead (`getConflictSuggestions`) | — | OK | — | orphaned client side |
| `DELETE /schedules/{id}` | `scheduleApi.delete` (ScheduleHistory) | — | — | 400/404/409→toast | 409 when a solve job is QUEUED/RUNNING (server-side, tested) |
| `POST /schedules/{id}/disruption/preview` | `scheduleApi.previewDisruption` (DisruptionHandling) | OK | OK | 400/404/409/422→toast | — |
| `POST /schedules/{id}/disruption/apply` | `scheduleApi.applyDisruption` (DisruptionHandling) | OK | OK | 400/404/409/422→toast | 202 + SolveJobResponse |
| `GET /schedules/{id}/export/csv` | `scheduleApi.exportCsv` (TimetableViewer) | — | OK | blob→generic msg | errors surface as `err.message` (blob body) |
| `GET /schedules/ical/teacher/{teacherId}` | `scheduleApi.downloadTeacherIcal` + inline URL (CalendarPortal) | — | OK | blob→generic msg | — |
| `GET /schedules/ical/batch/{batchId}` | `scheduleApi.downloadBatchIcal` + inline URL (CalendarPortal) | — | OK | blob→generic msg | — |
| `POST /schedules/feasibility-check` | `scheduleApi.checkFeasibility` (ScheduleGenerator) | OK | OK | 400/404/409/422→toast | — |
| `GET /solve-jobs` | FE dead (`getAll`) | — | OK | — | orphaned client side |
| `GET /solve-jobs/{id}` | `solveJobApi.getById` (useSolveJob hook; SolveProgress, TimetableViewer, DisruptionHandling, Events) | — | OK | — | 2 s poll loop |
| `GET /solve-jobs/schedule/{scheduleId}` | FE dead (`listForSchedule`) | — | OK | — | orphaned client side |
| `POST /solve-jobs/{id}/cancel` | `solveJobApi.cancel` (SolveProgress) | — | OK | 400/404/409→toast | — |
| `PATCH /sessions/{id}` | `sessionApi.updateAssignment` (TimetableViewer) | OK | OK | 400/404/409/422→toast | server-side HARD-constraint gate (see Part B); `@Valid` added this pass |
| `GET /sessions/schedule/{scheduleId}` | `scheduleApi.getSessions` (above) | — | OK | — | duplicated route shape, both live |
| `GET /sessions/schedule/{scheduleId}/batch/{batchId}` | **no backend caller** | — | OK | — | orphaned endpoint |
| `GET /sessions/schedule/{scheduleId}/teacher/{teacherId}` | **no backend caller** | — | OK | — | orphaned endpoint |
| `GET /events` | `eventApi.getAll` (Events, DisruptionHandling) | — | OK | — | LocalDate ISO strings |
| `GET /events/{id}` | FE dead | — | OK | — | — |
| `POST /events` | `eventApi.create` | OK | OK | 400/404/409→toast | title `@NotBlank`, dates `@NotNull` (this pass verified) |
| `PUT /events/{id}` | `eventApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /events/{id}` | `eventApi.delete` | — | — | 400/404/409→toast | — |
| `POST /events/{eventId}/apply/{scheduleId}` | `eventApi.applyToSchedule` (Events) | — | OK | 400/404/409/422→toast | 202 + SolveJobResponse |
| `GET /academic-terms` | `academicTermApi.getAll` (AcademicTerms) | — | OK | — | — |
| `POST /academic-terms` | `academicTermApi.create` | OK | OK | 400/404/409→toast | — |
| `PUT /academic-terms/{id}` | `academicTermApi.update` | OK | OK | 400/404/409→toast | — |
| `DELETE /academic-terms/{id}` | `academicTermApi.delete` | — | — | 400/404/409→toast | — |
| `GET /academic-terms/{id}` | FE dead | — | OK | — | — |
| `POST /import/zip` | `importApi.importZip` (CsvImport) | OK | OK | 400/413/409→toast | multipart + `dryRun`; size caps in service |
| `GET /import/export/zip` | `importApi.exportZip` (CsvImport) | — | OK | blob→generic msg | — |
| `POST /import/csv/{entityType}` | **no backend caller** | OK | OK | — | orphaned endpoint (flat-CSV path; UI is ZIP-only since the import rename) |
| `GET /import/export/csv/{entityType}` | **no backend caller** | — | OK | — | orphaned endpoint |
| `GET /import/order` | **no backend caller** | — | OK | — | orphaned endpoint (dependency-order metadata, unused by UI) |
| `POST /pre-allocations` | **no backend caller** | — | — | — | whole controller orphaned (unchanged from initial commit) |
| `GET /pre-allocations/{id}` | **no backend caller** | — | — | — | orphaned |
| `GET /pre-allocations/schedule/{scheduleId}` | **no backend caller** | — | — | — | orphaned |
| `DELETE /pre-allocations/{id}` | **no backend caller** | — | — | — | orphaned |

### Response-shape checks beyond the table

- `ClassSessionResponse` serializes `day`/`startTime`/`endTime`; the viewer
  reads them as strings — matches `HH:mm` (nominal/effective start/end from
  `ClassSessionServiceImpl.toResponse` and `ScheduleServiceImpl`).
- `DisruptionResponse.ImpactedSession` uses string day/time — matches TS.
- `SolveJobResponse` field names (`jobType`, `errorMessage`, etc.) were aligned
  with the record in an earlier fix; verified current side-by-side — no other
  DTO pair carries a format assumption beyond `LocalTime HH:mm` and ISO dates.
- Optionality drift found: `TeacherRequest.movementPenalty` (TS optional,
  Java bare `int` → defaults 0 when omitted — safe), `SubjectRequest.minGap*
  (TS optional, Java `@Min(0)` int → defaults 0 — safe), `UniversityConfigRequest.active`
  (TS sends, Java ignores — documented above). All are server-strict or inert;
  none can silently corrupt data.

## Part B — Backend enforcement findings

1. **@Valid on every @RequestBody** — audited all 29 `@RequestBody`
   parameters across 16 controllers. The only one missing `@Valid` was
   `ClassSessionController.updateAssignment` → **fixed** (commit
   `fix(api): add @Valid to PATCH /sessions/{id} request body`). Jakarta
   annotations on entities remain dead code by design; enforcement point is
   DTO + `@Valid` on the method, and that is now uniform. (411/422 statuses:
   `MethodArgumentNotValidException` → 400, `IllegalArgumentException` → 400,
   `IllegalStateException` → 422, duplicates/busy/integrity → 409.)
2. **Manual-assignment gate (L11)** — verified closed server-side on
   `PATCH /sessions/{id}` (`ClassSessionServiceImpl.updateAssignment`):
   locked-session guard; teacher must be qualified for the subject
   (`requireTeacherValid`); room must match `roomTypeRequired`/lab subtype and
   capacity ≥ session seats (`requireRoomValid`); CLASS-only timeslots; teacher/
   room availability lists; same-day teacher/room/section/batch conflicts with
   solver-equal overlap semantics. **Schedule-scope note:** the `Schedule`
   entity persists only the scope *enum* — the resource lists of
   `ScheduleRequest` (batchIds/teacherIds/roomIds) are used at generation and
   not persisted, so a PATCH cannot be checked against a persisted scope set;
   the same-day conflict checks against the schedule's own sessions are the
   enforceable core and are enforced. Called directly (bypassing the UI) the
   endpoint rejects unqualified teachers, over-capacity rooms, non-CLASS
   timeslots, and unavailable resources with 400 + a `ProblemDetail` message.
3. **Timeslot integrity** — enforced server-side regardless of form UI:
   `start < end`, no same-day overlap, unique positive `slotNumber`
   (`TimeslotServiceImpl.validateTimeslotRequest`).
4. **Schedule deletion vs running solve** — enforced server-side
   (`ScheduleServiceImpl.delete` → `ensureNoActiveJobForSchedule`, 409
   `ResourceBusyException`), with unit tests (`delete_rejectsWhenSolveJobIsInProgress`).
5. **Feasibility gate** — `generate` refuses infeasible requests server-side
   (422) before persisting anything; the wizard's pre-check button is a UX
   convenience, not the guard.
6. **Role/permission gating** — explicit statement: **there is no auth layer
   in ARARE** (no Spring Security dependency, no session/principal handling,
   no role checks in any controller). Nothing in the frontend implies
   authenticated roles either (no route guards, no role-gated UI). The
   "matching backend check for role-gated UI" item is therefore **N/A**, not
   silently assumed covered.
7. **Frontend-only enforcement (not load-bearing):** the remaining UI-side
   rules are cosmetic/UX-only — disable states, `min`/`max` affordances,
   sort order, and the elapsed-time progress fill in `SolveProgress`
   (labeled honestly as `Solving… Ns of target` on top of real job status).
   None protects a server invariant. Blob-download error messages degrade to
   the generic axios message (noted in Part A); listing only.

## Part C / D — see final audit summary comment

Compile/boot/E2E results and the deliberate non-fixes are reported in the
session summary, not duplicated here.