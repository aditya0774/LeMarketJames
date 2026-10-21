-- 029: Trade records are never changed or deleted (BR-14, contracts/C2-audit.md)
--
-- 016 locked the audit trail. The records it describes are locked here the same way:
--
--   orders                  - an order moves through its lifecycle, so it stays updatable while it
--                             is open. Once FILLED or REJECTED it is final (contract C1) and never
--                             changes again. No order is ever deleted.
--   settlement_receipts     - what trading-service settled for each order (012).
--   cash_movement_receipts  - each deposit and withdrawal (024).
--   account_status_changes  - who suspended, closed or expired an account, and why (027).
--
-- Two independent layers, as in 016:
--
--   1. lemarket_app may not DELETE or TRUNCATE any of them, and may only read and add rows to the
--      three insert-only tables ("permission denied", SQLSTATE 42501).
--   2. Triggers refuse those changes for every role, the owner included (SQLSTATE LM001): a
--      mistaken grant, a migration or a script with the owner's password.
--
-- An account that ever moved cash can't be deleted either: its receipts would go with it
-- (024's ON DELETE CASCADE), and the receipts' trigger refuses that.
--
-- No application path changes a final order: Order.transitionTo refuses every move out of
-- FILLED or REJECTED, and the fill writes FILLED in the same UPDATE that clears settlement_pending.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on). The row
-- triggers fire per row, so the old delete in 009 still passes when re-applied: it matches no rows.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. What the application account may do
-- ---------------------------------------------------------------------------
-- Orders: read, place and move along the lifecycle; never remove.
REVOKE DELETE, TRUNCATE ON orders FROM lemarket_app;

-- Insert-only. 016's default privileges gave lemarket_app full rights on the tables created after
-- it; keep only read and add. (027 already did this for account_status_changes; restated so this
-- file alone shows the whole lockdown.)
REVOKE ALL ON settlement_receipts, cash_movement_receipts, account_status_changes FROM lemarket_app;
GRANT SELECT, INSERT ON settlement_receipts, cash_movement_receipts, account_status_changes TO lemarket_app;

-- ---------------------------------------------------------------------------
-- 2. The second layer: nobody changes a trade record, whatever they were granted
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trade_record_refuse_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- LM001, as for audit records (016): a caller tells this refusal from a missing privilege.
    RAISE EXCEPTION 'Trade records are immutable: % on % refused', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'LM001',
              HINT = 'Final orders, receipts and account status history are never changed or deleted; see contracts/C2-audit.md.';
END $$;

-- A final order: per row, and only when the row was already FILLED or REJECTED, so the lifecycle
-- (an open order becoming final) still works.
CREATE OR REPLACE TRIGGER orders_final_immutable
    BEFORE UPDATE ON orders
    FOR EACH ROW WHEN (OLD.order_status IN ('FILLED', 'REJECTED'))
    EXECUTE FUNCTION trade_record_refuse_change();
CREATE OR REPLACE TRIGGER orders_no_delete
    BEFORE DELETE ON orders
    FOR EACH ROW EXECUTE FUNCTION trade_record_refuse_change();
CREATE OR REPLACE TRIGGER orders_no_truncate
    BEFORE TRUNCATE ON orders
    FOR EACH STATEMENT EXECUTE FUNCTION trade_record_refuse_change();

-- The insert-only tables: no UPDATE, DELETE or TRUNCATE at all. Per row for UPDATE and DELETE, so
-- a statement that touches no record is not an error.
CREATE OR REPLACE TRIGGER settlement_receipts_immutable_row
    BEFORE UPDATE OR DELETE ON settlement_receipts
    FOR EACH ROW EXECUTE FUNCTION trade_record_refuse_change();
CREATE OR REPLACE TRIGGER settlement_receipts_immutable_truncate
    BEFORE TRUNCATE ON settlement_receipts
    FOR EACH STATEMENT EXECUTE FUNCTION trade_record_refuse_change();

CREATE OR REPLACE TRIGGER cash_movement_receipts_immutable_row
    BEFORE UPDATE OR DELETE ON cash_movement_receipts
    FOR EACH ROW EXECUTE FUNCTION trade_record_refuse_change();
CREATE OR REPLACE TRIGGER cash_movement_receipts_immutable_truncate
    BEFORE TRUNCATE ON cash_movement_receipts
    FOR EACH STATEMENT EXECUTE FUNCTION trade_record_refuse_change();

CREATE OR REPLACE TRIGGER account_status_changes_immutable_row
    BEFORE UPDATE OR DELETE ON account_status_changes
    FOR EACH ROW EXECUTE FUNCTION trade_record_refuse_change();
CREATE OR REPLACE TRIGGER account_status_changes_immutable_truncate
    BEFORE TRUNCATE ON account_status_changes
    FOR EACH STATEMENT EXECUTE FUNCTION trade_record_refuse_change();

-- ALWAYS: they also fire when session_replication_role is 'replica'. Stated on every run, so
-- re-applying this file also re-arms a trigger that was left disabled.
ALTER TABLE orders                 ENABLE ALWAYS TRIGGER orders_final_immutable;
ALTER TABLE orders                 ENABLE ALWAYS TRIGGER orders_no_delete;
ALTER TABLE orders                 ENABLE ALWAYS TRIGGER orders_no_truncate;
ALTER TABLE settlement_receipts    ENABLE ALWAYS TRIGGER settlement_receipts_immutable_row;
ALTER TABLE settlement_receipts    ENABLE ALWAYS TRIGGER settlement_receipts_immutable_truncate;
ALTER TABLE cash_movement_receipts ENABLE ALWAYS TRIGGER cash_movement_receipts_immutable_row;
ALTER TABLE cash_movement_receipts ENABLE ALWAYS TRIGGER cash_movement_receipts_immutable_truncate;
ALTER TABLE account_status_changes ENABLE ALWAYS TRIGGER account_status_changes_immutable_row;
ALTER TABLE account_status_changes ENABLE ALWAYS TRIGGER account_status_changes_immutable_truncate;

COMMIT;
