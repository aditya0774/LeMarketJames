-- 007: LeBronified instrument names
--
-- A bit of fun: every company in the simulated market gets a LeBron-themed display name.
-- Only instruments.name changes. Tickers stay real (AAPL, MSFT, ...) so orders, holdings,
-- quotes and tests that refer to symbols keep working, and ids are never renumbered.
--
--   1. Rename the six existing instruments. tradable is not touched, so GOOGL stays
--      non-tradable (005) and CI can keep relying on it.
--   2. Add two more US equities: LMT (LeBronHeed Martin Corp) and AVGO (Broncom Inc).
--   3. Give the new instruments simulation parameters. Instruments without params are not
--      quoted; with no market_quotes row the simulator starts them from initial_price.
--
-- IDEMPOTENT: Safe to run more than once. On a fresh Docker volume Postgres runs this
-- file automatically, and CI (Jenkinsfile) applies it again afterwards.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Rename existing instruments
-- ---------------------------------------------------------------------------
UPDATE instruments i
SET name = v.name
FROM (VALUES
    ('AAPL',  'AppLe Inc'),
    ('MSFT',  'MicroBron Corp'),
    ('GOOGL', 'Alphabron Inc'),
    ('AMZN',  'Amabron.com Inc'),
    ('TSLA',  'TesLe Inc'),
    ('NVDA',  'Nvidibron Corp')
) AS v (ticker, name)
WHERE i.ticker = v.ticker;

-- ---------------------------------------------------------------------------
-- 2. Additional instruments
-- ---------------------------------------------------------------------------
INSERT INTO instruments (ticker, name, asset_class, currency, tradable, location) VALUES
    ('LMT',  'LeBronHeed Martin Corp', 'EQUITY', 'USD', TRUE, 'US'),
    ('AVGO', 'Broncom Inc',            'EQUITY', 'USD', TRUE, 'US')
ON CONFLICT (ticker) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Simulation parameters for the new instruments
-- ---------------------------------------------------------------------------
-- Roughly realistic values: LMT is a steady, low-volatility defence stock with a large
-- dividend; AVGO is a chip stock that moves more like NVDA.
INSERT INTO instrument_market_params (
    instrument_id, initial_price, drift, volatility, market_correlation, spread_bps,
    shares_outstanding, avg_daily_volume, earnings_per_share, dividend_per_share)
SELECT i.instrument_id, v.initial_price, v.drift, v.volatility, v.market_correlation, v.spread_bps,
       v.shares_outstanding, v.avg_daily_volume, v.earnings_per_share, v.dividend_per_share
FROM (VALUES
    ('LMT',  460.0000, 0.070000, 0.200000, 0.4000, 1.5,  237000000,  1200000, 27.5000, 13.2000),
    ('AVGO', 170.0000, 0.120000, 0.420000, 0.5500, 2.0, 4700000000, 25000000,  2.8000,  2.1000)
) AS v (ticker, initial_price, drift, volatility, market_correlation, spread_bps,
        shares_outstanding, avg_daily_volume, earnings_per_share, dividend_per_share)
JOIN instruments i ON i.ticker = v.ticker
ON CONFLICT (instrument_id) DO NOTHING;

COMMIT;
