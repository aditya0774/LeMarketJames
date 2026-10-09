-- Add index on filled_at for fast range queries in trade reports
-- This significantly speeds up the reporting_trades view queries used by reporting-service
CREATE INDEX IF NOT EXISTS orders_filled_at_idx ON orders(filled_at);
