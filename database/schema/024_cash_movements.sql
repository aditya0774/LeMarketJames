-- 024: Idempotent deposits and withdrawals (LMKT-149, contracts/C6-api.md)
--
-- A request ID identifies one cash movement. Keeping its outcome lets trading-service answer a
-- retry without changing the account twice. The account row and receipt are written in the same
-- transaction, alongside the audit event.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

CREATE TABLE IF NOT EXISTS cash_movement_receipts (
    request_id         UUID PRIMARY KEY,
    account_id         INTEGER NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    movement_type      VARCHAR(10) NOT NULL CHECK (movement_type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount             NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    cash_balance_after NUMERIC(19,2) NOT NULL CHECK (cash_balance_after >= 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS cash_movement_receipts_account_idx
    ON cash_movement_receipts (account_id, created_at);

-- Event types mirror com.lemarketjames.common.audit.AuditEventType. Migrations 010 and 014 carry
-- the same list for clean databases and because Jenkins safely reapplies the schema files.
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_action_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check
    CHECK (action IN ('SUBMITTED', 'RULE_CHECKED', 'VALIDATED', 'ACCEPTED', 'FILLED', 'REJECTED', 'SETTLED', 'CASH_DEPOSITED', 'CASH_WITHDRAWN'));

COMMIT;
