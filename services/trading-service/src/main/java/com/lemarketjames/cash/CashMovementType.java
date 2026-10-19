package com.lemarketjames.cash;

import com.lemarketjames.common.audit.AuditEventType;

/** The two ways cash can move after an account is opened. */
public enum CashMovementType {
    DEPOSIT,
    WITHDRAWAL;

    AuditEventType auditType() {
        return this == DEPOSIT ? AuditEventType.CASH_DEPOSITED : AuditEventType.CASH_WITHDRAWN;
    }
}
