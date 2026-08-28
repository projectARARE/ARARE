-- Rename stored ScheduleScope enum value COLLEGE -> INSTITUTE so the persisted
-- scope text matches the domain terminology (Institute == College). Idempotent:
-- if no rows carry the old value, this is a no-op.
UPDATE schedules SET scope = 'INSTITUTE' WHERE scope = 'COLLEGE';
