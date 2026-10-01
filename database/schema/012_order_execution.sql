-- Durable execution intent and idempotent settlement outcomes. Safe to reapply.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS settlement_pending BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS settlement_receipts (
    order_id INTEGER PRIMARY KEY,
    account_id INTEGER NOT NULL,
    instrument_id INTEGER NOT NULL,
    order_type VARCHAR(10) NOT NULL CHECK (order_type IN ('BUY', 'SELL')),
    quantity NUMERIC(14,4) NOT NULL CHECK (quantity > 0),
    price_per_unit NUMERIC(14,4) NOT NULL CHECK (price_per_unit > 0),
    rejection_reason VARCHAR(255)
);
-- No order FK: settlement must commit independently, and receipts outlive order cleanup.
CREATE INDEX IF NOT EXISTS orders_execution_idx ON orders(order_id)
    WHERE order_status NOT IN ('FILLED', 'REJECTED');
