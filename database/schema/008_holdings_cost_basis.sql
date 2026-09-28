-- Cost basis for holdings, so gain/loss can be computed for real instead of hardcoded to zero.
-- Populated going forward by holdings-service's settlement logic (see HoldingsSettlementService)
-- when a BUY order fills; pre-existing seeded holdings default to 0 (no historical fill to derive
-- it from) and will show as 100% gain until a real trade re-establishes their cost basis.
--
-- IDEMPOTENT: Docker initialization and Jenkins both apply this migration, so the column is only
-- added when missing (and existing cost basis is preserved on reruns).
ALTER TABLE holdings ADD COLUMN IF NOT EXISTS average_cost NUMERIC(14,4) NOT NULL DEFAULT 0;
