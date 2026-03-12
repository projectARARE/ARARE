package com.arare.features.impact;

public enum DependencyType {
    /** Two sessions share the same teacher. */
    TEACHER,
    /** Two sessions share the same room. */
    ROOM,
    /** Two sessions are for the same batch. */
    BATCH
}
