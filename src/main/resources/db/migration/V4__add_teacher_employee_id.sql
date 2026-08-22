-- V4: Add employee_id to teachers for deterministic natural-key CSV import.
-- -----------------------------------------------------------------------------
-- Nullable so existing teacher rows are not broken.
-- A partial unique index is used so that NULL values do not participate in the
-- uniqueness check (two teachers can both have NULL employee_id – they just
-- cannot both have the same non-null employee_id).
-- -----------------------------------------------------------------------------

ALTER TABLE teachers ADD COLUMN IF NOT EXISTS employee_id VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS uk_teachers_employee_id
    ON teachers (employee_id)
    WHERE employee_id IS NOT NULL;
