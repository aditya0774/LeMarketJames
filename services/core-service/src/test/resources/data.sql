-- Test-only seed data, loaded into the H2 test database after Hibernate creates the tables
-- (see spring.jpa.defer-datasource-initialization in this folder's application.properties).
--
-- Mirrors the instruments and market parameters from database/schema/001, 006 and 007 so
-- integration tests (e.g. QuotesControllerTest) can request real quotes. Keep in sync when
-- the instrument seed changes.

INSERT INTO instruments (ticker, name, asset_class, currency, tradable, location) VALUES
    ('AAPL',  'AppLe Inc',              'EQUITY', 'USD', TRUE,  'US'),
    ('MSFT',  'MicroBron Corp',         'EQUITY', 'USD', TRUE,  'US'),
    ('GOOGL', 'Alphabron Inc',          'EQUITY', 'USD', FALSE, 'US'),
    ('AMZN',  'Amabron.com Inc',        'EQUITY', 'USD', TRUE,  'US'),
    ('TSLA',  'TesLe Inc',              'EQUITY', 'USD', TRUE,  'US'),
    ('NVDA',  'Nvidibron Corp',         'EQUITY', 'USD', TRUE,  'US'),
    ('LMT',   'LeBronHeed Martin Corp', 'EQUITY', 'USD', TRUE,  'US'),
    ('AVGO',  'Broncom Inc',            'EQUITY', 'USD', TRUE,  'US');

INSERT INTO instrument_market_params (
    instrument_id, initial_price, drift, volatility, market_correlation, spread_bps,
    shares_outstanding, avg_daily_volume, earnings_per_share, dividend_per_share)
SELECT i.instrument_id, v.initial_price, v.drift, v.volatility, v.market_correlation, v.spread_bps,
       v.shares_outstanding, v.avg_daily_volume, v.earnings_per_share, v.dividend_per_share
FROM (VALUES
    ('AAPL',  227.5500, 0.080000, 0.250000, 0.6500, 1.5, 15200000000,  55000000,  6.7500,  1.0000),
    ('MSFT',  429.8500, 0.090000, 0.230000, 0.6500, 1.5,  7430000000,  22000000, 12.1000,  3.3200),
    ('GOOGL', 175.3000, 0.090000, 0.280000, 0.6000, 2.0, 12300000000,  25000000,  7.5400,  0.8000),
    ('AMZN',  197.1500, 0.100000, 0.320000, 0.6000, 2.0, 10500000000,  40000000,  5.5300,  0.0000),
    ('TSLA',  244.2000, 0.100000, 0.550000, 0.4500, 3.0,  3200000000,  85000000,  2.0400,  0.0000),
    ('NVDA',  131.8000, 0.120000, 0.480000, 0.5500, 2.0, 24500000000, 250000000,  2.9400,  0.0400),
    ('LMT',   460.0000, 0.070000, 0.200000, 0.4000, 1.5,   237000000,   1200000, 27.5000, 13.2000),
    ('AVGO',  170.0000, 0.120000, 0.420000, 0.5500, 2.0,  4700000000,  25000000,  2.8000,  2.1000)
) AS v (ticker, initial_price, drift, volatility, market_correlation, spread_bps,
        shares_outstanding, avg_daily_volume, earnings_per_share, dividend_per_share)
JOIN instruments i ON i.ticker = v.ticker;
