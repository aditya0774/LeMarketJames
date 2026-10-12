-- 019: An order can have one alert per reason (LMKT-71, contracts/C6-api.md "The consumers")
--
-- 018 gave order_alerts one row per order, because the only alert was for a large order and an
-- order is placed once. Trading Ops are now also alerted when an order's settlement is refused,
-- and a large order can be refused too, so the same order needs a row for each reason.
--
-- This migration:
--   1. Replaces UNIQUE (order_id) with UNIQUE (order_id, reason). It is still what makes an event
--      delivered twice the same event: one alert per order and reason.
--   2. Lets instrument_id, side, quantity and submitted_at be empty. They describe the order as
--      it was placed, which only the OrderSubmitted event carries. An alert raised from an
--      OrderStatusChanged event knows the order, its account and why, not what was ordered.
--   3. Adds rejection_reason: why the order was rejected, for an alert about a rejection.
--   4. Adds occurred_at: when the thing the alert is about happened. Alerts are listed newest
--      first by it, since an alert about a rejection has no submitted_at to list it by. Existing
--      alerts are about a placement, so theirs is their submitted_at.
--
-- Apply it before starting the updated surveillance-service, and with the old one stopped: the old
-- one does not fill occurred_at, so its inserts would be refused.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on). 018 is
-- re-applied before this file and puts order_alerts_submitted_idx back; step 4 drops it again.
--
-- Note: the old key was declared inline in 018, so Postgres named it order_alerts_order_id_key.

BEGIN;

-- 1. One alert per order and reason.
ALTER TABLE order_alerts DROP CONSTRAINT IF EXISTS order_alerts_order_id_key;
ALTER TABLE order_alerts DROP CONSTRAINT IF EXISTS order_alerts_order_reason_key;
ALTER TABLE order_alerts ADD CONSTRAINT order_alerts_order_reason_key UNIQUE (order_id, reason);

-- 2. What was ordered is known only to an alert raised when the order was placed.
ALTER TABLE order_alerts ALTER COLUMN instrument_id DROP NOT NULL;
ALTER TABLE order_alerts ALTER COLUMN side DROP NOT NULL;
ALTER TABLE order_alerts ALTER COLUMN quantity DROP NOT NULL;
ALTER TABLE order_alerts ALTER COLUMN submitted_at DROP NOT NULL;

-- 3. A RejectionReason code (buy-sell-service) as the event carried it, never free text. No CHECK:
--    the list lives in RejectionReason, and a code added there needs no change here.
ALTER TABLE order_alerts ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(40);

-- 4. When what the alert is about happened.
ALTER TABLE order_alerts ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ;
UPDATE order_alerts SET occurred_at = submitted_at WHERE occurred_at IS NULL;
ALTER TABLE order_alerts ALTER COLUMN occurred_at SET NOT NULL;

DROP INDEX IF EXISTS order_alerts_submitted_idx;
CREATE INDEX IF NOT EXISTS order_alerts_occurred_idx ON order_alerts (occurred_at DESC);

COMMIT;
