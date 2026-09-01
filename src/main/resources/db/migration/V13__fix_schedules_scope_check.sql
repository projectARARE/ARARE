-- The scope value COLLEGE was renamed to INSTITUTE (see V12) but the CHECK
-- constraint from V2 still listed the old value, so new INSTITUTE-scope
-- schedules could never be inserted. Recreate the constraint to match the
-- ScheduleScope enum.
ALTER TABLE schedules DROP CONSTRAINT IF EXISTS schedules_scope_check;
ALTER TABLE schedules ADD CONSTRAINT schedules_scope_check
    CHECK (scope in ('DEPARTMENT','INSTITUTE','UNIVERSITY'));
