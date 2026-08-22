package com.arare.exception;

// Thrown when an operation is rejected because a resource is currently in
// use by an in-flight process (e.g. deleting a schedule while a solve job
// for it is QUEUED or RUNNING). Mapped to HTTP 409 Conflict.
public class ResourceBusyException extends RuntimeException {

    public ResourceBusyException(String message) {
        super(message);
    }
}
