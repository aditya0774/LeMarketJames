package com.lemarketjames.common.audit;

import java.util.Map;

/**
 * One event of an order submission's audit trail (contract C2). Unlike the other lifecycle events
 * it is identified by the request that caused it, because a refused submission has no order.
 *
 * @param requestId the server-generated ID shared by every event of one submission
 * @param type      what happened
 * @param rule      the rule checked, for {@link AuditEventType#RULE_CHECKED}; otherwise null
 * @param orderId   the saved order, or null when the submission was refused
 * @param accountId the caller's account, or null when they were refused access to it
 * @param clientId  the authenticated caller, not resolved from the account (it may not be theirs)
 * @param details   the keys documented on {@code type}
 */
public record SubmissionAuditEvent(String requestId, AuditEventType type, String rule, Integer orderId,
                                   Integer accountId, Integer clientId, Map<String, Object> details) {

    /**
     * What makes the event unique: a submission has one event per type, and one per rule for
     * {@link AuditEventType#RULE_CHECKED}. Stored in {@code audit_log.event_key}, which is unique.
     */
    public String eventKey() {
        return requestId + ":" + type + (rule == null ? "" : ":" + rule);
    }
}
