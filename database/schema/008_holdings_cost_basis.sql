-- Cost basis for holdings, so gain/loss can be computed for real instead of hardcoded to zero.
-- Populated going forward by holdings-service's settlement logic (see HoldingsSettlementService)
-- when a BUY order fills; pre-existing seeded holdings default to 0 (no historical fill to derive
-- it from) and will show as 100% gain until a real trade re-establishes their cost basis.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'holdings' AND column_name = 'average_cost'
  ) THEN
    ALTER TABLE holdings ADD COLUMN average_cost NUMERIC(14,4) NOT NULL DEFAULT 0;
  END IF;
END $$;
