-- V8: Teacher term teaching allotments + class curricula.
-- -----------------------------------------------------------------------------
-- teacher_assignments: the "allotted" layer of teacher duty (which teacher
-- actually teaches which subject, for which batch/section, this term).
-- The solver enforces allotments as a HARD constraint (teacherNotAssignedToClass);
-- when no allotment exists it falls back to Teacher.subjects (qualified), so
-- the feature is backward compatible.
--
-- Scope rule (service + entity enforced): exactly one of batch_id / section_id.
-- Uniqueness: one teacher per (subject, batch) and per (subject, section).
-- Postgres treats NULLs as distinct in unique indexes, so the two scope keys
-- need separate partial indexes instead of a single nullable composite key.
--
-- batch_subjects / class_section_subjects: curriculum join tables. A batch (or
-- a lab section) declares the subjects it actually offers this term; the
-- session generator scopes to these so electives/specialisations never create
-- phantom sessions. Empty curriculum = inherit everything the department offers.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS teacher_assignments (
    id            BIGSERIAL PRIMARY KEY,
    teacher_id    BIGINT       NOT NULL,
    subject_id    BIGINT       NOT NULL,
    batch_id      BIGINT,
    section_id    BIGINT,
    weekly_hours  INT,
    priority      INT          NOT NULL DEFAULT 1,
    notes         VARCHAR(400),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_teacher_assignments_teacher ON teacher_assignments (teacher_id);
CREATE INDEX IF NOT EXISTS idx_teacher_assignments_batch   ON teacher_assignments (batch_id);
CREATE INDEX IF NOT EXISTS idx_teacher_assignments_section ON teacher_assignments (section_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_teacher_assignments_subject_batch
    ON teacher_assignments (subject_id, batch_id) WHERE section_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_teacher_assignments_subject_section
    ON teacher_assignments (subject_id, section_id) WHERE section_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_teacher_assignments_teacher') THEN
        ALTER TABLE teacher_assignments ADD CONSTRAINT fk_teacher_assignments_teacher
            FOREIGN KEY (teacher_id) REFERENCES teachers (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_teacher_assignments_subject') THEN
        ALTER TABLE teacher_assignments ADD CONSTRAINT fk_teacher_assignments_subject
            FOREIGN KEY (subject_id) REFERENCES subjects (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_teacher_assignments_batch') THEN
        ALTER TABLE teacher_assignments ADD CONSTRAINT fk_teacher_assignments_batch
            FOREIGN KEY (batch_id) REFERENCES batches (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_teacher_assignments_section') THEN
        ALTER TABLE teacher_assignments ADD CONSTRAINT fk_teacher_assignments_section
            FOREIGN KEY (section_id) REFERENCES class_sections (id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS batch_subjects (
    batch_id   BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    PRIMARY KEY (batch_id, subject_id)
);

CREATE TABLE IF NOT EXISTS class_section_subjects (
    section_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    PRIMARY KEY (section_id, subject_id)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_batch_subjects_batch') THEN
        ALTER TABLE batch_subjects ADD CONSTRAINT fk_batch_subjects_batch
            FOREIGN KEY (batch_id) REFERENCES batches (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_batch_subjects_subject') THEN
        ALTER TABLE batch_subjects ADD CONSTRAINT fk_batch_subjects_subject
            FOREIGN KEY (subject_id) REFERENCES subjects (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_section_subjects_section') THEN
        ALTER TABLE class_section_subjects ADD CONSTRAINT fk_section_subjects_section
            FOREIGN KEY (section_id) REFERENCES class_sections (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_section_subjects_subject') THEN
        ALTER TABLE class_section_subjects ADD CONSTRAINT fk_section_subjects_subject
            FOREIGN KEY (subject_id) REFERENCES subjects (id);
    END IF;
END $$;