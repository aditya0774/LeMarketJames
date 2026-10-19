package com.lemarketjames.common.audit;

import java.util.Map;

/** One deposit or withdrawal audit event, identified by its idempotency request ID. */
public record CashAuditEvent(
        String requestId,
        Integer accountId,
        Integer clientId,
        AuditEventType type,
        Map<String, Object> details) {

    public CashAuditEvent {
        if (type != AuditEventType.CASH_DEPOSITED && type != AuditEventType.CASH_WITHDRAWN) {
            throw new IllegalArgumentException("Cash audit event requires a cash movement type");
        }
        details = Map.copyOf(details);
    }
}
