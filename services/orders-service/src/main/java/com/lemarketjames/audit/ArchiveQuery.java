package com.lemarketjames.audit;

import java.time.Instant;

/**
 * One archive search (contract C6, "Audit archive"). Every filter is optional and they combine
 * with AND; {@code from} is inclusive and {@code to} exclusive. Validated on construction, so a
 * query that exists is one the repository can run.
 */
public record ArchiveQuery(Integer orderId, String requestId, Integer clientId, Instant from, Instant to,
                           int limit, int offset) {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;

    public ArchiveQuery {
        if ((orderId != null && orderId <= 0) || (clientId != null && clientId <= 0)) {
            throw new IllegalArgumentException("IDs must be positive integers");
        }
        // request_id is a UUID string, at most 36 characters (audit_log.request_id).
        if (requestId != null && (requestId.isBlank() || requestId.length() > 36)) {
            throw new IllegalArgumentException("requestId must be 1 to 36 characters");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (limit < 1 || limit > MAX_LIMIT || offset < 0) {
            throw new IllegalArgumentException("limit must be 1 to " + MAX_LIMIT + " and offset 0 or more");
        }
    }
}
