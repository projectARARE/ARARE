package com.arare.exception;

// Thrown when a schedule generation/solve request is infeasible (e.g. a subject
// has no qualified teacher, or more sessions are required than timeslots exist).
// Mapped to HTTP 422 by GlobalExceptionHandler.
public class InfeasibleScheduleException extends RuntimeException {

    public InfeasibleScheduleException(String message) {
        super(message);
    }

    public InfeasibleScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}
