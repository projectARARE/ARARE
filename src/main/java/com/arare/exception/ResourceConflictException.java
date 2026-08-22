package com.arare.exception;

// A hard scheduling conflict that overlaps another live (ACTIVE) schedule —
// e.g. a manual edit that double-books a teacher at the same day+time in a
// schedule that is already in use. Maps to HTTP 409 CONFLICT.
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}