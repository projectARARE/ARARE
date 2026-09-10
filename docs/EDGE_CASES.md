# Edge Cases — Status of Record

The real-university timetable edge cases that drove product decisions, and how
each one is handled today. Everything below is **enforced end-to-end** (backend
uniqueness + cross-scope guards + CSV import safety + frontend pickers). There
are no deliberately-open items; the last three (solver repair, teacher→institute
binding, institute-aware CSV targeting) were implemented in the production
finalization wave.

## Domain / data-governance edges

| # | Edge case | Behavior today | Status |
|---|-----------|----------------|--------|
| 1 | Two institutes both have a department `CS` | Department code/name unique per `(institute, code)`; same code in a different institute is legal (`V14`). CSV import treats a code shared across institutes as **ambiguous** and refuses to silently prefer one. | Enforced |
| 2 | Department owned by institute A but assigned institute B's building | `department.buildingIds` must be a subset of the institute's buildings; the department form only offers the owning institute's buildings. | Enforced |
| 3 | Institute-wide subject (no department) paired with any batch | Allowed by contract: `departmentId == null` subjects may pair with any batch regardless of department. | Enforced |
| 4 | Subject from dept X offered/assigned/sessioned to a batch of dept Y | Cross-scope mismatch rejected with 409 in offerings, teacher assignments, manual sessions and pre-allocations. The frontend forms auto-align subject↔batch to one department so this is impossible by construction. | Enforced |
| 5 | Duplicate subject code within one department | Unique `(department_id, code)` partial index; institute-wide subjects unique among themselves by code. | Enforced |
| 6 | Two buildings named identically in different institutes | Buildings are institute-owned (`V14`). CSV import keeps an ambiguity set over building names: a name that exists in multiple institutes makes name-only targeting refuse the row with a clear error (an explicit `instituteCode` column disambiguates). | Enforced |
| 7 | Same `room_number` in two buildings | Already unique per `(building_id, room_number)`; room import keys by `building\|room`. | OK |
| 8 | Batch `(dept, year, section)` and section `(batch, label)` duplicates | Already unique per parent. | OK |
| 9 | CSV departments/buildings targeting institute 2 | Departments.csv and buildings.csv now accept an optional `instituteCode` column resolved by code then name; absent it defaults to the single/first institute, correct for single-campus deployments. `(institute, code)` resolution + ambiguity sets keep multi-institute seeding safe. | Enforced |

## Timetabling / solver edges

| # | Edge case | Behavior today | Status |
|---|-----------|----------------|--------|
| 10 | Input that is genuinely infeasible (e.g. too few labs for a department, or a teacher whose availability cannot cover load) | Solver drives hard score to a best effort and the schedule resolves to `INFEASIBLE`; status is surfaced in ScheduleHistory and a score explanation reports the offending constraint. Feasibility check (`FeasibilityCheckService`) predicts infeasibility on DRAFT before committing. | OK |
| 11 | Solver stuck at a small residual hard score (the historical `-1hard` seed flake) | `SolveJobRunner` reserves part of the budget (≤60s, up to ¼) and, when the main solve finishes with `hardScore < 0`, runs up to two fresh-solver repair rounds from the best solution, keeping the better score and stopping early once feasible. | Enforced |
| 12 | Cross-schedule double-booking when departments share a building | Solve and session edits scan active schedules of the same institute (institute-scoped gift of busy intervals); distinct institutes do not see each other's rooms. | Enforced |
| 13 | Lab subject with no lab of the required subtype in scope | Qualified room picker hides unsuitable rooms and explains "No suitable room"; solver hard-constrains lab subtype. | Enforced |
| 14 | Venue capacity below batch size | Feasibility check hard-constrains capacity; `QualifiedRoomSelect` passes `minCapacity` (the batch's `studentCount`) in ManualSessions and TimetableViewer create/edit so undersized rooms are never offered. | Enforced |
| 15 | Teacher movement between distant buildings | `movementPenalty` + `preferredBuildingIds` are soft objectives; institute-owned buildings, department/institute solve pools, and institute-scoped teacher/room pools confine sessions so cross-campus sessions are reduced. | Enforced |
| 16 | Free-day conflicts (teacher free day vs batch working day) | Hard constraints model availability; batches/teachers expose `preferredFreeDay` + `workingDays`. | Enforced |
| 17 | Break/blocked timeslots & exam-blocked days | Timeslot `type` (`CLASS`/`BREAK`/`BLOCKED`) and schedule `blockedDays` keep sessions out of non-class slots. | Enforced |
| 18 | Editing locked sessions / publishing a DRAFT mid-edit | Session locks block mutation of locked sessions; lifecycle guards reject revalidate of ACTIVE (409) while **ARCHIVED schedules can be re-validated back to an editable DRAFT/INFEASIBLE** (that is the unarchive path). DRAFT remains fully mutable. | Enforced |

## Seeded-data formation

The regression/seed generator (`scripts/e2e-university-test.py`) derives every
natural key and display name from the **live institute's code** so the dataset
stays unique across institutes:

- Departments `ARARE-D01..D08`, buildings `ARARE-Building 1..N`, subjects
  `ARARE-D01-S1..`, batches `(ARARE-D01, year, section)`, teachers `EMP-0001..`.
- `buildings.csv` and `departments.csv` carry an `instituteCode` column, so the
  same seed can be replayed against a second campus without name/lookup
  collisions and without ever silently preferring one institute.

## Deviation note

Soft-penalty tuning (movement / gap / over-load weights as user-configurable
schedule options) was considered and deliberately **not** exposed: the defaults
already produce balanced timetables and the knob adds UI + persistence surface
without a demonstrated deployment need (KISS/YAGNI). Re-introduce only if a real
university asks for it.