package com.arare.features.schedule;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// Request body for a partial re-solve: the minimal set of session IDs that
// must be re-scheduled after a disruption. Empty is rejected so a malformed
// request can never degrade silently into a full re-solve.
public record PartialResolveRequest(@NotEmpty(message = "impactedSessionIds must not be empty") List<Long> impactedSessionIds) {}
