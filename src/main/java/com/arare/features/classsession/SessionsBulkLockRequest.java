package com.arare.features.classsession;

import java.util.List;

/**
 * Bulk lock-toggle request. When sessionIds is null or empty the whole
 * schedule is toggled; otherwise only the listed sessions are updated.
 * Lock toggling never changes an assignment, so no hard-constraint
 * revalidation is required.
 */
public record SessionsBulkLockRequest(
    boolean locked,
    List<Long> sessionIds
) {}