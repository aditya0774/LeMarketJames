-- The quote an order was priced from at execution (LMKT-23 AC3). Safe to reapply.
-- price_per_unit already holds the quote's price and filled_at the execution time; these two
-- columns add where the quote came from and when the feed produced it.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS quote_source VARCHAR(50);
-- TIMESTAMPTZ like audit_log.created_at: a quote time is a UTC instant, not an order-local stamp.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS quote_time TIMESTAMPTZ;
