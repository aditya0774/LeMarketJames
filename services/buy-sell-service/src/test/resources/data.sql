-- Test-only seed data, loaded into the H2 test database after Hibernate creates the tables
-- (see spring.jpa.defer-datasource-initialization in this folder's application.properties).
--
-- Mirrors the instruments from database/schema/001/006/007 so order tests have real instruments
-- to reference.

INSERT INTO instruments (ticker, name, asset_class, currency, tradable, location) VALUES
    ('AAPL',  'AppLe Inc',              'EQUITY', 'USD', TRUE,  'US'),
    ('MSFT',  'MicroBron Corp',         'EQUITY', 'USD', TRUE,  'US'),
    ('GOOGL', 'Alphabron Inc',          'EQUITY', 'USD', FALSE, 'US'),
    ('AMZN',  'Amabron.com Inc',        'EQUITY', 'USD', TRUE,  'US'),
    ('TSLA',  'TesLe Inc',              'EQUITY', 'USD', TRUE,  'US'),
    ('NVDA',  'Nvidibron Corp',         'EQUITY', 'USD', TRUE,  'US'),
    ('LMT',   'LeBronHeed Martin Corp', 'EQUITY', 'USD', TRUE,  'US'),
    ('AVGO',  'Broncom Inc',            'EQUITY', 'USD', TRUE,  'US');
