-- V6: Seven-day calendar support (SUNDAY) + per-schedule blocked days.
--
-- 1) Widen the inline CHECK constraints that enumerate allowed days so a
--    SUNDAY value is accepted on already-migrated databases.
-- 2) Relax university_configs.days_per_week from 5..6 to 1..7.
-- 3) Create schedule_blocked_days (new @ElementCollection on schedules).

-- batch_working_days.day
ALTER TABLE batch_working_days DROP CONSTRAINT IF EXISTS batch_working_days_day_check;
ALTER TABLE batch_working_days ADD CONSTRAINT batch_working_days_day_check
    CHECK (day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'));

-- batches.preferred_free_day
ALTER TABLE batches DROP CONSTRAINT IF EXISTS batches_preferred_free_day_check;
ALTER TABLE batches ADD CONSTRAINT batches_preferred_free_day_check
    CHECK (preferred_free_day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'));

-- config_working_days.day
ALTER TABLE config_working_days DROP CONSTRAINT IF EXISTS config_working_days_day_check;
ALTER TABLE config_working_days ADD CONSTRAINT config_working_days_day_check
    CHECK (day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'));

-- teachers.preferred_free_day
ALTER TABLE teachers DROP CONSTRAINT IF EXISTS teachers_preferred_free_day_check;
ALTER TABLE teachers ADD CONSTRAINT teachers_preferred_free_day_check
    CHECK (preferred_free_day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'));

-- timeslots.day
ALTER TABLE timeslots DROP CONSTRAINT IF EXISTS timeslots_day_check;
ALTER TABLE timeslots ADD CONSTRAINT timeslots_day_check
    CHECK (day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'));

-- university_configs.days_per_week (1..7)
ALTER TABLE university_configs DROP CONSTRAINT IF EXISTS university_configs_days_per_week_check;
ALTER TABLE university_configs ADD CONSTRAINT university_configs_days_per_week_check
    CHECK ((days_per_week <= 7) AND (days_per_week >= 1));

-- Per-schedule blocked days (Schedule.blockedDays)
CREATE TABLE IF NOT EXISTS schedule_blocked_days (
    schedule_id bigint not null,
    day varchar(255) CHECK (day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'))
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_schedule_blocked_days_schedule') THEN
        ALTER TABLE schedule_blocked_days ADD CONSTRAINT fk_schedule_blocked_days_schedule
            FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE;
    END IF;
END $$;
