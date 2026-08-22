-- V9: Institute layer above Department.
-- -----------------------------------------------------------------------------
-- institutes: constituent colleges/institutes within the university. Most
-- deployments have exactly one (hidden from the UI); multi-institute
-- universities get one row per college, each owning its own departments.
--
-- Backfill rule (non-breaking): create a single default institute and assign
-- EVERY existing department to it, so existing single-institute data is
-- untouched and every department row satisfies the new NOT NULL FK.
--
-- Also adds schedules.institute_id (nullable — null = university-wide) and
-- solve_jobs.institute_id so cross-schedule teacher-conflict queries can be
-- scoped per institute or run university-wide for shared teachers.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS institutes (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- Backfill: one default institute; all pre-existing departments land in it.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM institutes) THEN
        INSERT INTO institutes (name, code, description, created_at, updated_at, version)
        VALUES ('Main Campus', 'MAIN', 'Default institute created by V9 migration.', NOW(), NOW(), 0);
    END IF;
END $$;

-- Add the column (guarded for re-runs against partially-migrated DBs).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'departments' AND column_name = 'institute_id') THEN
        ALTER TABLE departments ADD COLUMN institute_id bigint;
    END IF;
END $$;

-- Backfill every department into the default institute before the NOT NULL
-- constraint is applied, so existing data is never rejected.
DO $$
DECLARE
    default_inst BIGINT;
BEGIN
    SELECT id INTO default_inst FROM institutes ORDER BY id LIMIT 1;
    IF default_inst IS NOT NULL THEN
        UPDATE departments SET institute_id = default_inst WHERE institute_id IS NULL;
    END IF;
END $$;

ALTER TABLE departments ALTER COLUMN institute_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_departments_institute') THEN
        ALTER TABLE departments ADD CONSTRAINT fk_departments_institute
            FOREIGN KEY (institute_id) REFERENCES institutes (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_departments_institute ON departments (institute_id);

-- Schedule / solve-job institute scoping (nullable; null = university-wide).
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS institute_id bigint;
ALTER TABLE solve_jobs  ADD COLUMN IF NOT EXISTS institute_id bigint;

CREATE INDEX IF NOT EXISTS idx_schedules_institute ON schedules (institute_id);
CREATE INDEX IF NOT EXISTS idx_solve_jobs_institute  ON solve_jobs  (institute_id);