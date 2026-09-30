-- 010: Schema for the shared contracts (LMKT-8, C1-C7)
--
-- What each change is for is described once, in contracts/ (linked per section below). The value
-- lists in the CHECK constraints mirror Java enums, which are the source of truth; change both
-- together.
--
--   1. staff_users            - staff logins and their role (contracts/C7-roles.md)
--   2. clients                - EXPIRED status, persistent login lockout, client segment
--                               (contracts/C3-seed-data.md, contracts/C5-config.md)
--   3. audit_log              - the audit event format (contracts/C2-audit.md)
--   4. suspended instrument   - the one non-tradable stock the seed data needs (contracts/C3-seed-data.md)
--   5. reporting_trades view  - the reporting data source (contracts/C6-api.md)
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Staff users (roles mirror com.lemarketjames.common.security.Role, minus CLIENT)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staff_users (
    staff_id              SERIAL PRIMARY KEY,
    username              VARCHAR(100) UNIQUE NOT NULL,
    email                 VARCHAR(100) UNIQUE NOT NULL,
    password              VARCHAR(255) NOT NULL, -- BCrypt hash, same as clients.password
    full_name             TEXT NOT NULL,
    role                  VARCHAR(20) NOT NULL
                          CHECK (role IN ('TRADING_OPS', 'ANALYST', 'COMPLIANCE')),
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 2. Clients
-- ---------------------------------------------------------------------------
-- Statuses mirror com.lemarketjames.common.domain.AccountStatus.
ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_account_status_check;
ALTER TABLE clients ADD CONSTRAINT clients_account_status_check
    CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED', 'EXPIRED'));

-- Lockout lives in the database so it survives restarts and applies across auth-service instances.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

-- Segments mirror com.lemarketjames.common.domain.ClientSegment.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS segment VARCHAR(20) NOT NULL DEFAULT 'RETAIL';
ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_segment_check;
ALTER TABLE clients ADD CONSTRAINT clients_segment_check
    CHECK (segment IN ('RETAIL', 'ACTIVE_TRADER', 'HIGH_NET_WORTH'));

-- ---------------------------------------------------------------------------
-- 3. Audit log (event types mirror com.lemarketjames.common.audit.AuditEventType)
-- ---------------------------------------------------------------------------
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS client_id INTEGER REFERENCES clients(client_id);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS details JSONB;

-- Convert the rows 001 seeded in the old format, so there is only one format in the table:
-- the old/new value pair moves into details, and the old action names map onto the new types.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'audit_log' AND column_name = 'old_values') THEN
        UPDATE audit_log SET details = jsonb_build_object('before', old_values, 'after', new_values)
        WHERE details IS NULL;
    END IF;
END $$;
UPDATE audit_log
SET action = CASE action
                 WHEN 'ORDER_ACCEPTED' THEN 'ACCEPTED'
                 WHEN 'ORDER_FILLED'   THEN 'FILLED'
                 ELSE 'SETTLED' -- HOLDING_UPDATED and CASH_ADJUSTED were the two halves of settlement
             END
WHERE action IN ('ORDER_ACCEPTED', 'ORDER_FILLED', 'HOLDING_UPDATED', 'CASH_ADJUSTED');
UPDATE audit_log l SET client_id = a.client_id
FROM accounts a
WHERE a.account_id = l.account_id AND l.client_id IS NULL;

-- One payload column: details replaces the old/new value pair.
ALTER TABLE audit_log DROP COLUMN IF EXISTS old_values;
ALTER TABLE audit_log DROP COLUMN IF EXISTS new_values;
-- Audit times are UTC instants. Existing values were written as UTC wall-clock times. Guarded because
-- converting a column that is already TIMESTAMPTZ again would shift it by the session's offset.
DO $$
BEGIN
    IF (SELECT data_type FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'audit_log' AND column_name = 'created_at') = 'timestamp without time zone' THEN
        ALTER TABLE audit_log ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
    END IF;
END $$;
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_action_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check
    CHECK (action IN ('SUBMITTED', 'VALIDATED', 'ACCEPTED', 'FILLED', 'REJECTED', 'SETTLED'));
CREATE INDEX IF NOT EXISTS audit_log_order_idx  ON audit_log (order_id);
CREATE INDEX IF NOT EXISTS audit_log_client_idx ON audit_log (client_id, created_at);

-- ---------------------------------------------------------------------------
-- 4. The suspended stock (id 52: ids 1-51 are the real market from 009)
-- ---------------------------------------------------------------------------
INSERT INTO instruments (instrument_id, ticker, name, asset_class, currency, tradable, location)
VALUES (52, 'CAVS', 'Cavaliers Media Group', 'EQUITY', 'USD', FALSE, 'US')
ON CONFLICT (ticker) DO NOTHING;

-- Explicit ids bypass the SERIAL sequence, so move it past them for any future plain INSERT.
SELECT setval(pg_get_serial_sequence('instruments', 'instrument_id'),
              (SELECT MAX(instrument_id) FROM instruments));

-- Market params so the suspended stock still has a live quote; it just can't be traded.
INSERT INTO instrument_market_params (
    instrument_id, initial_price, drift, volatility, market_correlation, spread_bps,
    shares_outstanding, avg_daily_volume, earnings_per_share, dividend_per_share)
SELECT instrument_id, 42.0000, 0.020000, 0.300000, 0.4000, 3.0, 180000000, 900000, 1.2000, 0.0000
FROM instruments WHERE ticker = 'CAVS'
ON CONFLICT (instrument_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Reporting data source: one row per filled order, ready for reports and insights
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW reporting_trades AS
SELECT o.order_id,
       o.account_id,
       c.client_id,
       c.segment,
       i.ticker                       AS symbol,
       i.name                         AS instrument_name,
       o.order_type                   AS side,
       o.quantity,
       o.price_per_unit,
       o.quantity * o.price_per_unit  AS gross_amount,
       o.submitted_at,
       o.filled_at
FROM orders o
JOIN accounts a    ON a.account_id = o.account_id
JOIN clients c     ON c.client_id = a.client_id
JOIN instruments i ON i.instrument_id = o.instrument_id
WHERE o.order_status = 'FILLED';

COMMIT;
