-- V7.2: NULLABLE pre-allocation timeslot + cross-schedule index.
-- -----------------------------------------------------------------------------
-- 1) pre_allocations.timeslot_id: relax to NULLABLE. A pre-allocation may now
--    pin only the teacher (and optionally room) and let the solver choose a
--    compatible slot (PreAllocationConstraintFact + preAllocationViolation
--    HARD constraint). The unique index uk_pre_allocations_schedule_batch_
--    subject_timeslot still applies -- Postgres treats NULLs as distinct, so
--    multiple slot-less pre-allocations for the same (schedule,batch,subject)
--    are permitted and single-teacher is enforced in the service layer.
-- 2) class_sessions(teacher_id, timeslot_id): backs the cross-schedule
--    busy-interval queries (findActiveCrossScheduleSessions /
--    findActiveCrossScheduleBusyIntervals) behind the 409 write-path gate and
--    the teacherBusyCrossSchedule solver constraint.
-- -----------------------------------------------------------------------------

ALTER TABLE pre_allocations ALTER COLUMN timeslot_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_class_sessions_teacher_timeslot
    ON class_sessions (teacher_id, timeslot_id);
