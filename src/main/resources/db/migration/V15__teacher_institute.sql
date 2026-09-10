-- V15: Institute binding for teachers.
-- -----------------------------------------------------------------------------
-- Adds an optional institute_id FK on teachers so a teacher can be scoped to a
-- constituent institute. NULL = university-global teacher (bound to no single
-- institute). Nullable on purpose — no backfill, so existing global teachers
-- stay global.
-- -----------------------------------------------------------------------------

ALTER TABLE teachers ADD COLUMN institute_id BIGINT REFERENCES institutes(id);
