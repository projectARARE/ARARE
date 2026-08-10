package com.arare.features.schedule;

import java.util.List;

// Request body for a partial re-solve: the minimal set of session IDs that
// must be re-scheduled after a disruption.
public record PartialResolveRequest(List<Long> impactedSessionIds) {}
