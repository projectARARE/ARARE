-- V14: Institute ownership for buildings + per-institute department uniqueness +
--      per-department subject uniqueness.
-- -----------------------------------------------------------------------------
-- 1. Buildings: add institute_id FK so every building is owned by an institute.
-- 2. Departments: drop the global UNIQUE on code/name and replace with a
--    per-institute UNIQUE(institute_id, code) so two institutes may use the
--    same department code/name.
-- 3. Subjects: add partial unique indexes enforcing code uniqueness within a
--    department (when department_id IS NOT NULL) or globally for institute-wide
--    subjects (department_id IS NULL).
-- -----------------------------------------------------------------------------

-- 1. Buildings — institute ownership
ALTER TABLE buildings ADD COLUMN institute_id BIGINT REFERENCES institutes(id);

-- Backfill every existing building into the default (first) institute.
UPDATE buildings b
   SET institute_id = (SELECT id FROM institutes ORDER BY id LIMIT 1)
 WHERE b.institute_id IS NULL;

ALTER TABLE buildings ALTER COLUMN institute_id SET NOT NULL;

ALTER TABLE buildings
    ADD CONSTRAINT fk_buildings_institute FOREIGN KEY (institute_id) REFERENCES institutes(id);

-- 2. Departments — per-institute uniqueness
-- Drop the global UNIQUE constraints on code and name.
-- PostgreSQL auto-names inline UNIQUE constraints as <table>_<column>_key.
ALTER TABLE departments DROP CONSTRAINT IF EXISTS departments_code_key;
ALTER TABLE departments DROP CONSTRAINT IF EXISTS departments_name_key;

-- Add composite unique constraint: same code allowed in different institutes.
ALTER TABLE departments
    ADD CONSTRAINT uq_departments_institute_code UNIQUE (institute_id, code);

-- 3. Subjects — per-department / institute-wide code uniqueness
-- Partial unique index: subject code unique within a department.
CREATE UNIQUE INDEX uq_subjects_department_code
    ON subjects (department_id, code)
    WHERE department_id IS NOT NULL;

-- Partial unique index: institute-wide subject code must be globally unique
-- (only one institute-wide subject per code).
CREATE UNIQUE INDEX uq_subjects_instwide_code
    ON subjects (code)
    WHERE department_id IS NULL;
