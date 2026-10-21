-- 031: The dollar amount an order was placed for (BR-04, contracts/C1-orders.md, contracts/C6-api.md)
--
-- A client may place an order by amount instead of by shares: "buy $250 of AAPL". orders-service
-- turns the amount into as many whole shares as it buys at the quote it priced the order from, and
-- saves that share count in orders.quantity like any other order. requested_amount keeps what the
-- client asked for, so the order history and the audit trail can show both. NULL for an order
-- placed by shares, which every order before this migration was.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS requested_amount NUMERIC(15,2);

-- Dropped and added again so a re-run never fails on an existing constraint.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_requested_amount_check;
ALTER TABLE orders ADD CONSTRAINT orders_requested_amount_check
    CHECK (requested_amount IS NULL OR requested_amount > 0);

COMMIT;
