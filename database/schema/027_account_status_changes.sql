-- 027: Account status history (BR-03, contracts/C7-roles.md, contracts/C6-api.md)
--
-- Every change to clients.account_status after registration gets one row here, in the same
-- transaction as the change: Trading Ops suspending, reinstating, closing or expiring an account,
-- and a client closing their own. The row says who did it and why, so a refused sign-in can always
-- be explained. No expiry date is kept: accounts are expired by Trading Ops, never automatically.
--
-- History is insert-only for the services: lemarket_app may read and add rows but not change or
-- delete them (as for the audit tables in 016).
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

CREATE TABLE IF NOT EXISTS account_status_changes (
    change_id   BIGSERIAL PRIMARY KEY,
    client_id   INTEGER NOT NULL REFERENCES clients(client_id),
    -- The same values as clients.account_status (010), so history never holds a status a client can't have.
    old_status  VARCHAR(20) NOT NULL CHECK (old_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED', 'EXPIRED')),
    new_status  VARCHAR(20) NOT NULL CHECK (new_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED', 'EXPIRED')),
    reason      VARCHAR(500) NOT NULL,
    -- The username of the staff member, or of the client closing their own account.
    changed_by  VARCHAR(100) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT account_status_changes_changed CHECK (old_status <> new_status)
);

-- Read one client at a time, newest first.
CREATE INDEX IF NOT EXISTS account_status_changes_client_idx
    ON account_status_changes (client_id, changed_at DESC);

-- 016's default privileges gave lemarket_app full rights on the new table; keep only read and add.
REVOKE ALL ON account_status_changes FROM lemarket_app;
GRANT SELECT, INSERT ON account_status_changes TO lemarket_app;
GRANT USAGE, SELECT ON SEQUENCE account_status_changes_change_id_seq TO lemarket_app;

COMMIT;
