-- V10: Subject offerings — the load-bearing curriculum model.
-- -----------------------------------------------------------------------------
-- subject_offerings decouples the catalogue (subjects) from what is actually
-- taught this term: ONE subject can be offered to MANY batches/sections
-- (shared/mega lectures, electives) and institute-wide subjects (subjects with
-- a NULL department_id) can be offered to batches of ANY institute.
--
-- Backward compatible: when a batch/section has no offerings the session
-- generator falls back to the legacy batch_subjects / class_section_subjects
-- join tables (empty = inherit all), so existing data keeps scheduling.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS subject_offerings (
    id           BIGSERIAL PRIMARY KEY,
    subject_id   BIGINT       NOT NULL,
    batch_id     BIGINT,
    section_id   BIGINT,
    weekly_hours INT,
    elective     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_subject_offerings_subject ON subject_offerings (subject_id);
CREATE INDEX IF NOT EXISTS idx_subject_offerings_batch   ON subject_offerings (batch_id);
CREATE INDEX IF NOT EXISTS idx_subject_offerings_section ON subject_offerings (section_id);

-- One offering per (subject, batch) and per (subject, section).
CREATE UNIQUE INDEX IF NOT EXISTS uq_subject_offerings_subject_batch
    ON subject_offerings (subject_id, batch_id) WHERE section_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subject_offerings_subject_section
    ON subject_offerings (subject_id, section_id) WHERE section_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_subject_offerings_subject') THEN
        ALTER TABLE subject_offerings ADD CONSTRAINT fk_subject_offerings_subject
            FOREIGN KEY (subject_id) REFERENCES subjects (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_subject_offerings_batch') THEN
        ALTER TABLE subject_offerings ADD CONSTRAINT fk_subject_offerings_batch
            FOREIGN KEY (batch_id) REFERENCES batches (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_subject_offerings_section') THEN
        ALTER TABLE subject_offerings ADD CONSTRAINT fk_subject_offerings_section
            FOREIGN KEY (section_id) REFERENCES class_sections (id);
    END IF;
END $$;

-- Institute-wide subjects: allow a subject with no owning department so it can
-- be offered to batches across every institute via subject_offerings.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'subjects' AND column_name = 'department_id'
                 AND is_nullable = 'NO') THEN
        ALTER TABLE subjects ALTER COLUMN department_id DROP NOT NULL;
    END IF;
END $$;
