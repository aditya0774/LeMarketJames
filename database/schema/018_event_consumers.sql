-- 018: Tables of the three Kafka consumers (contracts/C6-api.md, "The consumers")
--
-- Each service that listens to the order events keeps what it makes of them in a table of its own:
--
--   notifications   notification-service   one row per status change of an order, for its client
--   order_alerts    surveillance-service   one row per order large enough to alert Trading Ops
--   trade_activity  activity-service       one row per fill, summed into traded volume per stock
--
-- None of them has a foreign key to orders, accounts or instruments. The rows are written from
-- events, after the order's own transaction; they must not stop an order or a test fixture from
-- being deleted, and a row may arrive for an order this database no longer holds.
--
-- An event can be delivered more than once (Kafka redelivers after a restart or a failed commit),
-- so each table has a unique key on what makes an event the same event. The services check it
-- before inserting; the constraint is what guarantees it.
--
-- lemarket_app needs no grant here: 016 gives it read and write on every table created later.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

CREATE TABLE IF NOT EXISTS notifications (
    notification_id SERIAL PRIMARY KEY,
    account_id      INTEGER NOT NULL,
    order_id        INTEGER NOT NULL,
    -- Order statuses as text, exactly as the event carries them. No CHECK: the list lives in
    -- Order.OrderStatus (contracts/C1-orders.md), and a status added there needs no change here.
    previous_status VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    message         VARCHAR(255) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- An order enters a status once, so this is one row per status change.
    CONSTRAINT notifications_order_status_key UNIQUE (order_id, status)
);
-- A client's notifications, newest first.
CREATE INDEX IF NOT EXISTS notifications_account_idx ON notifications (account_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS order_alerts (
    alert_id      SERIAL PRIMARY KEY,
    -- One alert per order: an order is placed once.
    order_id      INTEGER NOT NULL UNIQUE,
    account_id    INTEGER NOT NULL,
    instrument_id INTEGER NOT NULL,
    side          VARCHAR(10) NOT NULL,
    quantity      NUMERIC(14,4) NOT NULL,
    -- Null for a SELL, which has no price until it fills.
    price         NUMERIC(14,4),
    -- An AlertReason code (surveillance-service), never free text.
    reason        VARCHAR(40) NOT NULL,
    submitted_at  TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS order_alerts_submitted_idx ON order_alerts (submitted_at DESC);

CREATE TABLE IF NOT EXISTS trade_activity (
    -- One row per filled order: an order fills once.
    order_id      INTEGER PRIMARY KEY,
    instrument_id INTEGER NOT NULL,
    side          VARCHAR(10) NOT NULL,
    quantity      NUMERIC(14,4) NOT NULL,
    price         NUMERIC(14,4) NOT NULL,
    filled_at     TIMESTAMPTZ NOT NULL
    -- No account: this is the market's activity, and it never says who traded.
);
-- The summary reads a recent window of fills and groups them by stock.
CREATE INDEX IF NOT EXISTS trade_activity_filled_idx ON trade_activity (filled_at, instrument_id);

COMMIT;
