-- V11: Disruption constraint facts on solve jobs.
-- -----------------------------------------------------------------------------
-- Partial-resolve jobs now carry the disruption they are repairing (encoded as
-- "TYPE:entityId:day" entries). The solver enforces these as HARD constraints so
-- "apply disruption" actually moves impacted sessions instead of leaving the
-- timetable unchanged while reporting success.

ALTER TABLE solve_jobs ADD COLUMN IF NOT EXISTS disruption_facts_csv TEXT;