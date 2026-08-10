-- V3: Integrity guard for the singleton active config rule.
-- -----------------------------------------------------------------------------
-- batchDailyClassesCapFromUniversityConfig joins UniversityConfig in the
-- constraint stream; if more than one config were active, the penalty would
-- be applied once per active row (effectively multiplied). The service layer
-- deactivates the previous active config before activating a new one, but
-- nothing prevented two active configs from existing via direct DB writes.
--
-- This migration enforces at-most-one active config at the database level
-- via a partial unique index (Postgres-only syntax — H2 tests disable
-- Flyway and use Hibernate's create-drop schema, so this is a Postgres guard).
-- -----------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS uq_university_configs_single_active
    ON university_configs (id)
    WHERE active = true;
