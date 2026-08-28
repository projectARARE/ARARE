# Schedule Export

ARARE can export a solved schedule to CSV, Excel (`.xlsx`), and PDF. All three
views render the same week grid: **rows = time slots, columns = days of the
week**, with each cell showing the subject (and teacher/room/building). Non-CLASS
sessions are intentionally excluded from the grid.

All classes live in `features/schedule/`.

## 1. `TimetableExportService` (`schedule/TimetableExportService.java`)

`exportCsv(scheduleId)` returns a flat UTF-8-BOM CSV (Excel-friendly) of assigned
sessions ordered by `day.ordinal()` then `startTime`. Columns:
`Day, Start, End, Subject, Code, Teacher, Room, Building, Batch, Section, Type,
Duration(h), Locked`.

* Only sessions with a `timeslot != null` are emitted.
* Unassigned sessions (no timeslot) are reported as a trailing comment line
  (`"# N session(s) were not assigned…"`) and excluded from the rows.
* Batch/section labels are derived from `ClassSession.getBatch()` /
  `getSection()` (lab-split sessions carry a `section`).

## 2. `ExcelExportService` (`schedule/ExcelExportService.java`)

`exportExcel(scheduleId, view, entityId)` with `View ∈ {ALL, TEACHER, BATCH, ROOM}`.

### Data fetch

* Loads CLASS timeslots, sorted by `startTime`, to define slot rows.
* `dayColumns(classSlots)` returns distinct `SchoolDay`s ordered by their
  `ordinal()` (so Monday→…→Friday column order).
* Loads all sessions for the schedule; counts and **logs** any session whose
  timeslot `type != CLASS` (excluded from the grid) – these are non-teaching
  slots (breaks/blocked) that have no place on the class timetable.
* `matches(s, view, entityId)` narrows to one teacher/batch/room (using
  `effectiveBatchId`, which resolves lab-split sections to their parent batch).

### Grid mapping

`renderGrid` builds the worksheet:

* Title row = schedule name; subtitle = entity label + session count + timestamp.
* Header row = `"Time"` + one column per day.
* Each CLASS timeslot is a row; for each day column, the cell shows
  `sessionText(cellSessions, view)` – subject name plus teacher (unless view is
  TEACHER, in which case the batch label is shown) and room · building.

### Sheet layout

* `View.ALL`: one sheet **per batch** (`groupByBatch`), using
  `uniqueSheetName` to guarantee valid, unique, ≤31-char Excel sheet names
  (collisions after truncation get `-2`, `-3`, …). Lab sections group under
  their parent batch.
* `View.TEACHER/BATCH/ROOM`: a single sheet narrowed to that entity.

### Streaming

Uses `SXSSFWorkbook` (streaming POI) over a 500-row window so memory stays flat
even for thousands of sessions. `exportRows` is a generic single-sheet helper
used by master-data tables (auto-detects numeric cells).

## 3. `PdfExportService` (`schedule/PdfExportService.java`)

`exportPdf(scheduleId, view, entityId)` mirrors the Excel grid with iText/lowagie:

* A4 **landscape** `Document`; a `PdfPTable` with `days.size() + 1` columns
  (time + one per day), `widths[0] = 1.6f` (wider time column).
* Header cells (day names) use an indigo background; time cells a light-grey
  background; session cells render subject (bold) + teacher/batch + room ·
  building.
* Same non-CLASS exclusion + logging as the Excel exporter.
* `matches`/`effectiveBatchId`/`entityLabel`/`dayColumns`/`batchLabel` logic is
  identical to `ExcelExportService`, so the two exports are guaranteed
  consistent.

## 4. Shared notes

* **Non-CLASS sessions excluded**: both Excel and PDF filter
  `timeslot != null && timeslot.type == CLASS` and log an info line per export.
  They are intentionally absent from the teaching grid.
* **EntityGraph-free fetch**: exporters load sessions via
  `sessionRepo.findByScheduleId` and rely on the LAZY associations being
  materialised within the read-only `@Transactional` method (subject/teacher/
  room/building are accessed directly). The CSV exporter likewise reads these
  within its transaction.
* **View narrowing** is purely a post-filter on sessions; the solver output is
  never altered – exports are read-only.
