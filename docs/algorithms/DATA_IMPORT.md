# Data Import (Relational CSV/ZIP)

ARARE ingests master data from RFC-4180 CSV files, either one entity type at a
time or as a ZIP archive containing entity files plus relationship files. The
import is **relational**: rows reference other rows by natural keys, not
database ids, and foreign keys are resolved in memory.

All classes live in `features/dataimport/`.

## 1. `CsvUtils` (`dataimport/CsvUtils.java`)

Shared parsing and natural-key normalisation.

* `parse(csv)` / `parse(csv, maxRows)` – RFC 4180 parser honouring quoted fields
  and `""` escapes. Header-only content → empty list. Refuses > `maxRows`
  (`MAX_DATA_ROWS_PER_FILE = 100_000`). A trailing UTF-8 BOM is stripped.
* Header normalisation: `normalizeHeader` lower-cases, removes spaces/underscores
  and the BOM, so `employeeId`, `EMPLOYEE ID`, `employee_id` all map to the same
  key.
* `write` / `toCsvLine` – RFC 4180 serialiser (prefixes a UTF-8 BOM so Excel
  opens UTF-8 correctly), with `escape` quoting `,` `"` and newlines.
* `required` / `blankToNull` / `splitTokens` (splits on `;` or `|`) /
  `parseEnum`, `parseBooleanOrDefault`, `parseIntOrDefault`, `optionalInt`,
  `parseLong`.
* **Natural-key builders** (mirror the DB UNIQUE constraints exactly):
  * `timeslotKey(day, start, end) = KEY(day)|normalizeTime(start)|normalizeTime(end)`
    – `normalizeTime` parses via `LocalTime` so CSV `"9:00"` matches stored
    `"09:00"`.
  * `roomKey(building, room) = KEY(building)|KEY(room)`.
  * `subjectKey(deptCode, subjectCode) = KEY(deptCode)|KEY(subjectCode)`.
  * `batchKey(deptCode, year, section) = KEY(deptCode)|year|KEY(section)`.
  * `key(raw)` / `normalize(raw)` upper-case, trim, strip BOM – case-insensitive
    comparisons everywhere.

## 2. `ImportContext` (`dataimport/ImportContext.java`)

Preloaded **natural-key indexes**, populated once per import from the DB
(`loadFromDatabase`) then updated in-memory via `register(...)` as files are
processed. All lookups are O(1) – no per-row DB scans.

Maps held: `deptByCode`, `buildingByKey`, `timeslotByKey` + `timeslotById`,
`roomByKey`, `subjectByKey`, `subjectByCode` (+ `ambiguousSubjectCodes` set),
`teacherByEmployeeId`, `batchByKey`.

`subjectByCode` returns `null` for a bare code that is ambiguous across
departments (the importer then requires the `DEPT:CODE` form). `containsAny`
reports whether any entity of a type is known (used to block empty imports).

## 3. `CsvEntityUpserter` (`dataimport/CsvEntityUpserter.java`)

The shared per-row upsert engine used by both single-entity and ZIP imports.
`upsert(type, row, rowNumber, context)` dispatches to one of
`upsertTimeslot/Building/Department/Room/Subject/Teacher/Batch`.

**Key invariant – validate-before-mutate:** every method resolves *all*
references and scalars (e.g. `departmentByCode`, `resolveBuildings`,
`resolveTimeslots`, `resolveSubjects`, `parseRequiredInt`) **before** touching
the (possibly managed) entity. Because a failure throws *before* any setter is
called, a skipped/errored row can never leave a partially-updated managed entity
to be flushed on commit – this is what makes "skip bad row, keep good rows"
transaction-safe.

Partial-update semantics: only columns present in the row are applied. Blank
optional columns keep existing values on update and fall back to defaults on
create. Token columns (`subjectCodes`, `availableTimeslots`,
`preferredBuildingNames`) *replace* the current set when present, and are left
untouched when blank – a CSV omitting them never wipes existing data.

* Timeslots keyed by `timeslotKey`; `slotNumber`/`type` defaulted on create.
* Teachers default `maxDailyHours=6`, `maxWeeklyHours=20`,
  `maxConsecutiveClasses=3`, `movementPenalty=1` on create.
* Batches default `studentCount=60`.
* Subject codes are upper-cased; `lab`/`requiresTeacher`/`requiresRoom` default
  true.

`upsert` returns `true` when a new entity was created, `false` on update. After
a successful save it calls `context.register(entity)` so later files (e.g.
relationship files, or later rows in the same file) can reference the new
natural key.

## 4. `RelationalCsvImportService` (`dataimport/RelationalCsvImportService.java`)

`importZip(file, dryRun)` runs in a single `@Transactional`:

### ZIP extraction safety (`extractZip`)

* Rejects archives > `MAX_ZIP_BYTES` (50 MiB).
* Streams each entry; aborts if combined uncompressed CSV bytes exceed
  `MAX_UNCOMPRESSED_CHARS` (4× the ZIP cap) – a **decompression-bomb guard**.
* Strips path components (`filename.contains("/")`) and lower-cases, so
  `subdir/Foo.CSV` → `foo.csv` – a **path-traversal strip** (files are read from
  an in-memory map keyed by bare name, never written to disk).

### PASS 1 – entity rows, dependency-ordered

Iterates `CsvEntityType.importOrder()` (see §5), and for each present
`*.csv` calls `processEntityFile`, which parses rows and calls
`upserter.upsert`, tallying created/updated/skipped + per-row errors.

### PASS 2 – relationship files (replace-per-mentioned-entity)

Each relationship CSV groups its rows by the owning entity and **replaces** that
entity's collection (clear + add) for *every mentioned* entity; un-mentioned
entities are untouched (partial update):

* `dept_buildings.csv` → `department.buildingsAllowed`
* `teacher_subjects.csv` → `teacher.subjects`
* `teacher_availability.csv` / `room_availability.csv` → `availableTimeslots`
* `teacher_preferred_buildings.csv` → `teacher.preferredBuildings`
* `batch_working_days.csv` → `batch.workingDays`
* `config_working_days.csv` / `config_break_indices.csv` → active
  `UniversityConfig` (`daysPerWeek` recomputed from working-day count)

FK resolution is entirely in memory via the `ImportContext` indexes built in
PASS 1.

### Dry-run / rollback

`markRollbackIfDryRun` sets the transaction `rollbackOnly` when `dryRun` is set,
so nothing is persisted while the response still reports the changes that
*would* have applied. Cascade ordering within the transaction is handled by JPA
repository `delete…` methods (see `CASCADE_DELETION.md`) rather than here.

## 5. `CsvEntityType` import order (`dataimport/CsvEntityType.java`)

`importOrder()` returns the enum values in dependency-safe order:

```
TIMESLOTS, BUILDINGS, DEPARTMENTS, ROOMS, SUBJECTS, TEACHERS, BATCHES
```

Dependencies declared per type (e.g. `ROOMS` depends on `BUILDINGS`,
`SUBJECTS` on `DEPARTMENTS`, `TEACHERS` on `DEPARTMENTS, SUBJECTS, TIMESLOTS,
BUILDINGS`, `BATCHES` on `DEPARTMENTS`) drive both the ZIP processing sequence
and single-entity validation. Because `ImportContext` is updated as each file is
processed, a later file can reference a key created earlier in the same import.
