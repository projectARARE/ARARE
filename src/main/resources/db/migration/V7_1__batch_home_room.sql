-- V7.1: Per-batch home lecture room.
-- -----------------------------------------------------------------------------
-- batches.home_room_id: a batch can nominate the CLASSROOM its lectures must
-- use, enforced by the homeRoomViolation HARD constraint (non-lab, non-LAB-
-- room-requiring sessions must land in this room). Nullable so existing rows
-- are untouched; the Batches page lets admins pick a room per batch.
-- -----------------------------------------------------------------------------

ALTER TABLE batches ADD COLUMN IF NOT EXISTS home_room_id bigint;

-- The whole migration is wrapped in a transaction; the name-guarded FK mirrors
-- V2's convention so re-runs against already-migrated databases are safe.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_batches_home_room') THEN
        ALTER TABLE batches ADD CONSTRAINT fk_batches_home_room
            FOREIGN KEY (home_room_id) REFERENCES rooms (id);
    END IF;
END $$;
