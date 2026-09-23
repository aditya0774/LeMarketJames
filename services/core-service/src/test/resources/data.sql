-- Test-only seed data, loaded into the H2 test database after Hibernate creates the tables
-- (see spring.jpa.defer-datasource-initialization in this folder's application.properties).
--
-- Mirrors the instruments from database/schema/001 so orders/holdings/quotes tests have real
-- instruments to reference. Market prices themselves come from a mocked MarketDataService now
-- that market-service owns instrument_market_params (see market-service's own test fixtures).

INSERT INTO instruments (ticker, name, asset_class, currency, tradable, location) VALUES
    ('AAPL',  'Apple Inc',      'EQUITY', 'USD', TRUE,  'US'),
    ('MSFT',  'Microsoft Corp', 'EQUITY', 'USD', TRUE,  'US'),
    ('GOOGL', 'Alphabet Inc',   'EQUITY', 'USD', FALSE, 'US'),
    ('AMZN',  'Amazon.com Inc', 'EQUITY', 'USD', TRUE,  'US'),
    ('TSLA',  'Tesla Inc',      'EQUITY', 'USD', TRUE,  'US'),
    ('NVDA',  'NVIDIA Corp',    'EQUITY', 'USD', TRUE,  'US');
