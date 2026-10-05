-- 014: Audit trail for order submission and validation (LMKT-99, contracts/C2-audit.md)
--
-- A refused order saves no order row but must still leave an audit trail, and no submission event
-- may be stored twice.
--
--   1. order_id / account_id become optional - a refused submission has no order, and a caller
--                                              refused access has no account of theirs to name
--   2. request_id                            - the server-generated ID shared by one submission's events
--   3. event_key                             - unique per submission, event type and rule
--   4. action                                - adds RULE_CHECKED
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

ALTER TABLE audit_log ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE audit_log ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_id VARCHAR(36);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS event_key VARCHAR(120);

-- Every event must still be traceable to something: an order, or the request that was refused.
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_identified_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_identified_check
    CHECK (order_id IS NOT NULL OR request_id IS NOT NULL);

-- The duplicate guard. Events that aren't part of a submission leave event_key NULL, and NULLs
-- never collide, so ACCEPTED/FILLED/REJECTED/SETTLED are unaffected.
CREATE UNIQUE INDEX IF NOT EXISTS audit_log_event_key_uq ON audit_log (event_key);
CREATE INDEX IF NOT EXISTS audit_log_request_idx ON audit_log (request_id);

-- Event types mirror com.lemarketjames.common.audit.AuditEventType; 010 carries the same list
-- because it is re-applied before this file.
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_action_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check
    CHECK (action IN ('SUBMITTED', 'RULE_CHECKED', 'VALIDATED', 'ACCEPTED', 'FILLED', 'REJECTED', 'SETTLED'));

COMMIT;
