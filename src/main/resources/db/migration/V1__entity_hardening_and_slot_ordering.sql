-- Entity hardening and slot-ordering migration.
-- Idempotent PostgreSQL migration for BOTH fresh and existing deployments.
-- On a fresh database, Hibernate ddl-auto=update creates tables AFTER Flyway,
-- so we guard every ALTER with a table-existence check.

-- -----------------------------------------------------------------------------
-- BaseEntity-derived audit/version columns
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    -- version column
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'academic_terms') THEN
        ALTER TABLE academic_terms ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'batches') THEN
        ALTER TABLE batches ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'buildings') THEN
        ALTER TABLE buildings ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'class_sections') THEN
        ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'departments') THEN
        ALTER TABLE departments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'events') THEN
        ALTER TABLE events ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'pre_allocations') THEN
        ALTER TABLE pre_allocations ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'rooms') THEN
        ALTER TABLE rooms ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'schedules') THEN
        ALTER TABLE schedules ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'subjects') THEN
        ALTER TABLE subjects ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'teachers') THEN
        ALTER TABLE teachers ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'timeslots') THEN
        ALTER TABLE timeslots ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'university_configs') THEN
        ALTER TABLE university_configs ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
    END IF;

    -- created_at / updated_at hardening for existing rows
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'academic_terms') THEN
        UPDATE academic_terms SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE academic_terms ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE academic_terms ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'batches') THEN
        UPDATE batches SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE batches ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE batches ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'buildings') THEN
        UPDATE buildings SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE buildings ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE buildings ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'class_sections') THEN
        UPDATE class_sections SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE class_sections ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE class_sections ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'departments') THEN
        UPDATE departments SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE departments ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE departments ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'events') THEN
        UPDATE events SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE events ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE events ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'pre_allocations') THEN
        UPDATE pre_allocations SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE pre_allocations ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE pre_allocations ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'rooms') THEN
        UPDATE rooms SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE rooms ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE rooms ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'schedules') THEN
        UPDATE schedules SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE schedules ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE schedules ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'subjects') THEN
        UPDATE subjects SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE subjects ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE subjects ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'teachers') THEN
        UPDATE teachers SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE teachers ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE teachers ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'timeslots') THEN
        UPDATE timeslots SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE timeslots ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE timeslots ALTER COLUMN updated_at SET NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'university_configs') THEN
        UPDATE university_configs SET created_at = COALESCE(created_at, NOW()), updated_at = COALESCE(updated_at, NOW());
        ALTER TABLE university_configs ALTER COLUMN created_at SET NOT NULL;
        ALTER TABLE university_configs ALTER COLUMN updated_at SET NOT NULL;
    END IF;

    -- Domain constraints / indexes
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'timeslots') THEN
        ALTER TABLE timeslots ADD COLUMN IF NOT EXISTS slot_number INTEGER;
    END IF;
END $$;

-- Indexes (CREATE INDEX IF NOT EXISTS is safe even if table doesn't exist yet in PG 9.5+,
-- but we guard anyway to be safe)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'buildings') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_buildings_name ON buildings (name);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'class_sections') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_class_sections_batch_label ON class_sections (batch_id, label);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'pre_allocations') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_pre_allocations_schedule_batch_subject_timeslot
            ON pre_allocations (schedule_id, batch_id, subject_id, timeslot_id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'timeslots') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_timeslots_day_slot_number
            ON timeslots (day, slot_number) WHERE slot_number IS NOT NULL;
    END IF;
END $$;
