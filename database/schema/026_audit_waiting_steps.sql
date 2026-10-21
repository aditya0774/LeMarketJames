-- 026: Audit the waiting steps of an order, PENDING and DELAYED (BR-15, contracts/C2-audit.md)
--
-- orders-service now records every status an order moves through, so a dispute can see when an
-- order waited for the market to open and when it started executing, not only that it was
-- accepted and then filled. The constraint is redefined in full, as AuditEventType lists it.
-- 010, 014 and 024 carry the same list: a clean database applies them all, after the seed (011)
-- has written PENDING and DELAYED events, and Jenkins re-applies them.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_action_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check
    CHECK (action IN ('SUBMITTED', 'RULE_CHECKED', 'VALIDATED', 'ACCEPTED', 'PENDING', 'DELAYED', 'FILLED', 'REJECTED', 'SETTLED', 'CASH_DEPOSITED', 'CASH_WITHDRAWN'));

COMMIT;
