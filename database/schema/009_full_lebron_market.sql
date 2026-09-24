-- 009: The full LeBronified market (50 tradable stocks)
--
-- Replaces the placeholder line-up from 001/006/007 with the final 50-stock market. Every
-- company gets its LeBronified parody name; tickers stay real so symbols read naturally.
--
--   1. Remove LMT (added by 007, not part of the final market) together with every row that
--      references it, because the instruments foreign keys have no ON DELETE CASCADE.
--   2. Rename the seven tickers that carry over (AAPL, MSFT, GOOGL, AMZN, TSLA, NVDA, AVGO) and
--      make them all tradable. This reverses 005: the NOT_TRADABLE checks in CI and the tests
--      now use their own throwaway fixture instead of a real stock.
--   3. Add the 43 new stocks with explicit ids 9-51. Explicit ids keep every database identical:
--      re-running 006/007 (as CI does) consumes SERIAL values even when ON CONFLICT skips the
--      row, and the frontend's InstrumentCatalog hard-codes these ids.
--   4. Upsert simulation parameters for all 50 so the whole market uses one consistent set of
--      roughly realistic values. Existing market_quotes snapshots are left alone, so live prices
--      carry on from where they are (initial_price only applies to instruments with no snapshot).
--
-- IDEMPOTENT: Safe to run more than once. On a fresh Docker volume Postgres runs this file
-- automatically, and CI (Jenkinsfile) applies it again afterwards.
--
-- Stop market-service before applying this to a running stack (and start it afterwards): it
-- keeps LMT in memory and would otherwise try to write LMT's quote back after the delete.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Remove LMT and everything that references it (children first)
-- ---------------------------------------------------------------------------
DELETE FROM audit_log
WHERE order_id IN (SELECT o.order_id FROM orders o
                   JOIN instruments i ON i.instrument_id = o.instrument_id
                   WHERE i.ticker = 'LMT');

DELETE FROM order_events
WHERE order_id IN (SELECT o.order_id FROM orders o
                   JOIN instruments i ON i.instrument_id = o.instrument_id
                   WHERE i.ticker = 'LMT');

DELETE FROM orders                   WHERE instrument_id IN (SELECT instrument_id FROM instruments WHERE ticker = 'LMT');
DELETE FROM holdings                 WHERE instrument_id IN (SELECT instrument_id FROM instruments WHERE ticker = 'LMT');
DELETE FROM price_candles            WHERE instrument_id IN (SELECT instrument_id FROM instruments WHERE ticker = 'LMT');
DELETE FROM market_quotes            WHERE instrument_id IN (SELECT instrument_id FROM instruments WHERE ticker = 'LMT');
DELETE FROM instrument_market_params WHERE instrument_id IN (SELECT instrument_id FROM instruments WHERE ticker = 'LMT');
DELETE FROM instruments              WHERE ticker = 'LMT';

-- ---------------------------------------------------------------------------
-- 2. Rename the carried-over instruments and make them all tradable
-- ---------------------------------------------------------------------------
UPDATE instruments i
SET name = v.name,
    tradable = TRUE
FROM (VALUES
    ('NVDA',  'BronVidia'),
    ('AAPL',  'BronApple'),
    ('GOOGL', 'AlphaBron'),
    ('MSFT',  'Bronisoft'),
    ('AMZN',  'AkronZon'),
    ('AVGO',  'Broncom'),
    ('TSLA',  'TesLe')
) AS v (ticker, name)
WHERE i.ticker = v.ticker;

-- ---------------------------------------------------------------------------
-- 3. The 43 new instruments (ids 9-51, in market-cap order)
-- ---------------------------------------------------------------------------
-- Non-US companies (TSM, ASML, BHP, SAN, TD, TTE, SHOP) are listed by their US listing/ADR, so
-- they trade in USD on US market hours like everything else.
INSERT INTO instruments (instrument_id, ticker, name, asset_class, currency, tradable, location) VALUES
    ( 9, 'TSM',   'Taiwan SemiBronductor',             'EQUITY', 'USD', TRUE, 'US'),
    (10, 'META',  'MetaBron Platforms',                'EQUITY', 'USD', TRUE, 'US'),
    (11, 'MU',    'MicBron Technology',                'EQUITY', 'USD', TRUE, 'US'),
    (12, 'LLY',   'eLe Bronny',                        'EQUITY', 'USD', TRUE, 'US'),
    (13, 'BRK-A', 'Bronshire Hathaway',                'EQUITY', 'USD', TRUE, 'US'),
    (14, 'AMD',   'Advanced Micro Bronvices',          'EQUITY', 'USD', TRUE, 'US'),
    (15, 'JPM',   'JPBron Chase',                      'EQUITY', 'USD', TRUE, 'US'),
    (16, 'WMT',   'BronMart',                          'EQUITY', 'USD', TRUE, 'US'),
    (17, 'V',     'VisaBron',                          'EQUITY', 'USD', TRUE, 'US'),
    (18, 'ASML',  'ASBron Holding',                    'EQUITY', 'USD', TRUE, 'US'),
    (19, 'XOM',   'Exxon MoBron',                      'EQUITY', 'USD', TRUE, 'US'),
    (20, 'JNJ',   'Bronson & Bronson',                 'EQUITY', 'USD', TRUE, 'US'),
    (21, 'INTC',  'BronTel',                           'EQUITY', 'USD', TRUE, 'US'),
    (22, 'MA',    'MasterBronCard',                    'EQUITY', 'USD', TRUE, 'US'),
    (23, 'ABBV',  'BronVie',                           'EQUITY', 'USD', TRUE, 'US'),
    (24, 'ORCL',  'Bronacle',                          'EQUITY', 'USD', TRUE, 'US'),
    (25, 'PLTR',  'PalanBron',                         'EQUITY', 'USD', TRUE, 'US'),
    (26, 'CSCO',  'BronCisco Systems',                 'EQUITY', 'USD', TRUE, 'US'),
    (27, 'CVX',   'CheBron',                           'EQUITY', 'USD', TRUE, 'US'),
    (28, 'COST',  'CostBronco',                        'EQUITY', 'USD', TRUE, 'US'),
    (29, 'BAC',   'Bank of Akron',                     'EQUITY', 'USD', TRUE, 'US'),
    (30, 'LRCX',  'LamBron Research',                  'EQUITY', 'USD', TRUE, 'US'),
    (31, 'KO',    'Coca-Bronla',                       'EQUITY', 'USD', TRUE, 'US'),
    (32, 'AMAT',  'Applied Bronterials',               'EQUITY', 'USD', TRUE, 'US'),
    (33, 'CAT',   'CaterBron',                         'EQUITY', 'USD', TRUE, 'US'),
    (34, 'MRK',   'Merck & Bron',                      'EQUITY', 'USD', TRUE, 'US'),
    (35, 'DELL',  'BronDell Technologies',             'EQUITY', 'USD', TRUE, 'US'),
    (36, 'PG',    'Bronter & Gamble',                  'EQUITY', 'USD', TRUE, 'US'),
    (37, 'IBM',   'International Bronsiness Machines', 'EQUITY', 'USD', TRUE, 'US'),
    (38, 'AMGN',  'AmBron',                            'EQUITY', 'USD', TRUE, 'US'),
    (39, 'BHP',   'BronHP Group',                      'EQUITY', 'USD', TRUE, 'US'),
    (40, 'LIN',   'LindBron',                          'EQUITY', 'USD', TRUE, 'US'),
    (41, 'SAN',   'Banco SantanBron',                  'EQUITY', 'USD', TRUE, 'US'),
    (42, 'QCOM',  'QualBroncomm',                      'EQUITY', 'USD', TRUE, 'US'),
    (43, 'TD',    'Toronto-Bronminion Bank',           'EQUITY', 'USD', TRUE, 'US'),
    (44, 'STX',   'SeaBron Technology',                'EQUITY', 'USD', TRUE, 'US'),
    (45, 'AXP',   'AmeriBron Express',                 'EQUITY', 'USD', TRUE, 'US'),
    (46, 'APH',   'AmphenBron',                        'EQUITY', 'USD', TRUE, 'US'),
    (47, 'TTE',   'TotalBronergies',                   'EQUITY', 'USD', TRUE, 'US'),
    (48, 'CRM',   'Bronforce',                         'EQUITY', 'USD', TRUE, 'US'),
    (49, 'VZ',    'VeriBron',                          'EQUITY', 'USD', TRUE, 'US'),
    (50, 'SHOP',  'ShopiBron',                         'EQUITY', 'USD', TRUE, 'US'),
    (51, 'DE',    'Deere & Bronpany',                  'EQUITY', 'USD', TRUE, 'US')
ON CONFLICT (ticker) DO NOTHING;

-- Explicit ids bypass the SERIAL sequence, so move it past them for any future plain INSERT.
SELECT setval(pg_get_serial_sequence('instruments', 'instrument_id'),
              (SELECT MAX(instrument_id) FROM instruments));

-- ---------------------------------------------------------------------------
-- 4. Simulation parameters for all 50
-- ---------------------------------------------------------------------------
-- Roughly realistic 2026 values, grouped by how the stock behaves:
--   * megacap tech / semis: volatility 0.24-0.50, strongly market-correlated
--   * high-beta names (TSLA, PLTR, MU, AMD, SHOP, INTC): volatility 0.50-0.65
--   * banks, pharma, staples, energy, telecom: volatility 0.15-0.25, lower correlation, dividends
-- Every price stays well under the $5,000 minimum deposit, so any new account can buy at least a
-- few whole shares of every stock (the trade dialog only allows whole shares). BRK-A is therefore
-- priced like Berkshire's class B share (1/1500 of a real class A share, ~$730k): shares outstanding
-- are scaled up and EPS down by the same factor, so market cap and P/E stay true to the company.
INSERT INTO instrument_market_params (
    instrument_id, initial_price, drift, volatility, market_correlation, spread_bps,
    shares_outstanding, avg_daily_volume, earnings_per_share, dividend_per_share)
SELECT i.instrument_id, v.initial_price, v.drift, v.volatility, v.market_correlation, v.spread_bps,
       v.shares_outstanding, v.avg_daily_volume, v.earnings_per_share, v.dividend_per_share
FROM (VALUES
    ('NVDA',     185.0000, 0.140000, 0.500000, 0.6000, 1.5, 24300000000, 180000000,     4.5000, 0.0400),
    ('AAPL',     255.0000, 0.090000, 0.260000, 0.6500, 1.5, 14800000000,  50000000,     7.5000, 1.0400),
    ('GOOGL',    245.0000, 0.100000, 0.300000, 0.6000, 1.5, 12100000000,  30000000,    10.0000, 0.8400),
    ('MSFT',     510.0000, 0.100000, 0.240000, 0.6500, 1.5,  7430000000,  20000000,    14.0000, 3.6400),
    ('AMZN',     225.0000, 0.110000, 0.330000, 0.6000, 1.5, 10700000000,  40000000,     6.5000, 0.0000),
    ('TSM',      290.0000, 0.120000, 0.400000, 0.6000, 2.0,  5190000000,  12000000,     9.5000, 3.0000),
    ('META',     720.0000, 0.110000, 0.380000, 0.6000, 2.0,  2520000000,  12000000,    27.0000, 2.1000),
    ('AVGO',     340.0000, 0.130000, 0.450000, 0.6000, 2.0,  4700000000,  20000000,     5.0000, 2.6000),
    ('TSLA',     420.0000, 0.100000, 0.600000, 0.5000, 2.5,  3220000000,  90000000,     1.7000, 0.0000),
    ('MU',       150.0000, 0.120000, 0.550000, 0.5500, 2.5,  1120000000,  20000000,     7.5000, 0.4600),
    ('LLY',      800.0000, 0.100000, 0.300000, 0.3500, 2.0,   897000000,   3500000,    22.0000, 6.0000),
    ('BRK-A',    490.0000, 0.080000, 0.180000, 0.5500, 1.5,  2160000000,   4000000,    30.0000, 0.0000),
    ('AMD',      170.0000, 0.120000, 0.520000, 0.6000, 2.0,  1620000000,  45000000,     2.5000, 0.0000),
    ('JPM',      300.0000, 0.080000, 0.220000, 0.6000, 1.5,  2750000000,   9000000,    20.0000, 6.0000),
    ('WMT',      100.0000, 0.070000, 0.180000, 0.4000, 1.5,  7970000000,  18000000,     2.6000, 0.9400),
    ('V',        345.0000, 0.090000, 0.200000, 0.5500, 1.5,  1930000000,   6500000,    11.0000, 2.3600),
    ('ASML',     950.0000, 0.110000, 0.400000, 0.5500, 2.5,   393000000,   1500000,    27.0000, 7.0000),
    ('XOM',      115.0000, 0.050000, 0.220000, 0.3500, 1.5,  4270000000,  15000000,     7.0000, 4.0000),
    ('JNJ',      180.0000, 0.050000, 0.160000, 0.3000, 1.5,  2410000000,   8000000,    10.5000, 5.2000),
    ('INTC',      35.0000, 0.040000, 0.500000, 0.5500, 3.0,  4370000000,  90000000,     0.3000, 0.0000),
    ('MA',       580.0000, 0.090000, 0.210000, 0.5500, 1.5,   905000000,   3000000,    16.0000, 3.0400),
    ('ABBV',     220.0000, 0.070000, 0.220000, 0.3000, 1.5,  1770000000,   6000000,    12.0000, 6.5600),
    ('ORCL',     280.0000, 0.110000, 0.450000, 0.5500, 2.0,  2810000000,  12000000,     4.5000, 2.0000),
    ('PLTR',     175.0000, 0.150000, 0.650000, 0.5500, 3.0,  2370000000,  60000000,     0.4000, 0.0000),
    ('CSCO',      70.0000, 0.070000, 0.220000, 0.5000, 1.5,  3960000000,  18000000,     2.5000, 1.6400),
    ('CVX',      160.0000, 0.050000, 0.240000, 0.3500, 1.5,  2050000000,   8000000,     9.0000, 6.8400),
    ('COST',     950.0000, 0.090000, 0.200000, 0.4000, 2.0,   443000000,   2000000,    18.0000, 5.2000),
    ('BAC',       50.0000, 0.070000, 0.250000, 0.6000, 2.0,  7500000000,  35000000,     3.6000, 1.1200),
    ('LRCX',     140.0000, 0.120000, 0.450000, 0.6000, 2.5,  1270000000,  10000000,     4.2000, 1.0400),
    ('KO',        70.0000, 0.050000, 0.150000, 0.3000, 1.5,  4300000000,  14000000,     2.5000, 2.0400),
    ('AMAT',     200.0000, 0.110000, 0.420000, 0.6000, 2.0,   800000000,   7000000,     8.5000, 1.8400),
    ('CAT',      480.0000, 0.080000, 0.280000, 0.5500, 1.5,   468000000,   2500000,    19.0000, 6.0400),
    ('MRK',       90.0000, 0.050000, 0.220000, 0.3000, 1.5,  2500000000,  12000000,     7.5000, 3.2400),
    ('DELL',     130.0000, 0.100000, 0.450000, 0.6000, 2.5,   680000000,   6000000,     6.5000, 2.1000),
    ('PG',       155.0000, 0.050000, 0.150000, 0.3000, 1.5,  2340000000,   7000000,     6.5000, 4.2300),
    ('IBM',      280.0000, 0.070000, 0.250000, 0.4500, 1.5,   930000000,   4500000,     6.5000, 6.7200),
    ('AMGN',     300.0000, 0.060000, 0.220000, 0.3000, 1.5,   538000000,   2500000,     8.0000, 9.5200),
    ('BHP',       55.0000, 0.060000, 0.280000, 0.4500, 2.5,  2540000000,   2500000,     3.8000, 2.2000),
    ('LIN',      460.0000, 0.080000, 0.180000, 0.4500, 1.5,   470000000,   2000000,    14.0000, 6.0000),
    ('SAN',       10.0000, 0.070000, 0.300000, 0.5500, 5.0, 14900000000,   5000000,     1.0500, 0.2500),
    ('QCOM',     165.0000, 0.080000, 0.350000, 0.6000, 2.0,  1090000000,   8000000,     9.5000, 3.5600),
    ('TD',        80.0000, 0.060000, 0.180000, 0.4500, 2.0,  1710000000,   2000000,     6.5000, 3.0000),
    ('STX',      250.0000, 0.120000, 0.500000, 0.5500, 2.5,   213000000,   3500000,     8.5000, 2.8800),
    ('AXP',      340.0000, 0.090000, 0.250000, 0.6000, 1.5,   697000000,   3000000,    15.0000, 3.2800),
    ('APH',      125.0000, 0.120000, 0.350000, 0.6000, 2.0,  1220000000,   8000000,     2.5000, 0.6600),
    ('TTE',       62.0000, 0.050000, 0.220000, 0.3500, 2.0,  2200000000,   1500000,     6.5000, 3.4000),
    ('CRM',      250.0000, 0.080000, 0.320000, 0.5500, 1.5,   955000000,   7000000,     6.5000, 1.6600),
    ('VZ',        42.0000, 0.040000, 0.170000, 0.3000, 1.5,  4220000000,  18000000,     4.2000, 2.7100),
    ('SHOP',     150.0000, 0.120000, 0.550000, 0.6000, 2.5,  1300000000,   9000000,     1.5000, 0.0000),
    ('DE',       480.0000, 0.070000, 0.250000, 0.5000, 1.5,   271000000,   1500000,    20.0000, 6.4800)
) AS v (ticker, initial_price, drift, volatility, market_correlation, spread_bps,
        shares_outstanding, avg_daily_volume, earnings_per_share, dividend_per_share)
JOIN instruments i ON i.ticker = v.ticker
ON CONFLICT (instrument_id) DO UPDATE SET
    initial_price      = EXCLUDED.initial_price,
    drift              = EXCLUDED.drift,
    volatility         = EXCLUDED.volatility,
    market_correlation = EXCLUDED.market_correlation,
    spread_bps         = EXCLUDED.spread_bps,
    shares_outstanding = EXCLUDED.shares_outstanding,
    avg_daily_volume   = EXCLUDED.avg_daily_volume,
    earnings_per_share = EXCLUDED.earnings_per_share,
    dividend_per_share = EXCLUDED.dividend_per_share;

COMMIT;
