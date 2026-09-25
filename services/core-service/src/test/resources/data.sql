-- Test-only seed data, loaded into the H2 test database after Hibernate creates the tables
-- (see spring.jpa.defer-datasource-initialization in this folder's application.properties).
--
-- Mirrors the 50 instruments of the final market (database/schema 001/006/007/009) so orders/holdings/
-- quotes tests have real instruments to reference. Tests look instruments up by ticker, so ids are left
-- to the identity column (explicit ids would not advance it, and OwnDataIntegrationTest inserts its own
-- instrument). Every stock is tradable; SellOrderIntegrationTest creates its own non-tradable fixture.
--
-- Market prices themselves come from a mocked MarketDataService now that market-service owns
-- instrument_market_params (see market-service's own test fixtures) -- core-service has no
-- instrument_market_params table at all anymore, so don't insert into it here.

INSERT INTO instruments (ticker, name, asset_class, currency, tradable, location) VALUES
    ('AAPL',  'BronApple',                         'EQUITY', 'USD', TRUE, 'US'),
    ('MSFT',  'Bronisoft',                         'EQUITY', 'USD', TRUE, 'US'),
    ('GOOGL', 'AlphaBron',                         'EQUITY', 'USD', TRUE, 'US'),
    ('AMZN',  'AkronZon',                          'EQUITY', 'USD', TRUE, 'US'),
    ('TSLA',  'TesLe',                             'EQUITY', 'USD', TRUE, 'US'),
    ('NVDA',  'BronVidia',                         'EQUITY', 'USD', TRUE, 'US'),
    ('AVGO',  'Broncom',                           'EQUITY', 'USD', TRUE, 'US'),
    ('TSM',   'Taiwan SemiBronductor',             'EQUITY', 'USD', TRUE, 'US'),
    ('META',  'MetaBron Platforms',                'EQUITY', 'USD', TRUE, 'US'),
    ('MU',    'MicBron Technology',                'EQUITY', 'USD', TRUE, 'US'),
    ('LLY',   'eLe Bronny',                        'EQUITY', 'USD', TRUE, 'US'),
    ('BRK-A', 'Bronshire Hathaway',                'EQUITY', 'USD', TRUE, 'US'),
    ('AMD',   'Advanced Micro Bronvices',          'EQUITY', 'USD', TRUE, 'US'),
    ('JPM',   'JPBron Chase',                      'EQUITY', 'USD', TRUE, 'US'),
    ('WMT',   'BronMart',                          'EQUITY', 'USD', TRUE, 'US'),
    ('V',     'VisaBron',                          'EQUITY', 'USD', TRUE, 'US'),
    ('ASML',  'ASBron Holding',                    'EQUITY', 'USD', TRUE, 'US'),
    ('XOM',   'Exxon MoBron',                      'EQUITY', 'USD', TRUE, 'US'),
    ('JNJ',   'Bronson & Bronson',                 'EQUITY', 'USD', TRUE, 'US'),
    ('INTC',  'BronTel',                           'EQUITY', 'USD', TRUE, 'US'),
    ('MA',    'MasterBronCard',                    'EQUITY', 'USD', TRUE, 'US'),
    ('ABBV',  'BronVie',                           'EQUITY', 'USD', TRUE, 'US'),
    ('ORCL',  'Bronacle',                          'EQUITY', 'USD', TRUE, 'US'),
    ('PLTR',  'PalanBron',                         'EQUITY', 'USD', TRUE, 'US'),
    ('CSCO',  'BronCisco Systems',                 'EQUITY', 'USD', TRUE, 'US'),
    ('CVX',   'CheBron',                           'EQUITY', 'USD', TRUE, 'US'),
    ('COST',  'CostBronco',                        'EQUITY', 'USD', TRUE, 'US'),
    ('BAC',   'Bank of Akron',                     'EQUITY', 'USD', TRUE, 'US'),
    ('LRCX',  'LamBron Research',                  'EQUITY', 'USD', TRUE, 'US'),
    ('KO',    'Coca-Bronla',                       'EQUITY', 'USD', TRUE, 'US'),
    ('AMAT',  'Applied Bronterials',               'EQUITY', 'USD', TRUE, 'US'),
    ('CAT',   'CaterBron',                         'EQUITY', 'USD', TRUE, 'US'),
    ('MRK',   'Merck & Bron',                      'EQUITY', 'USD', TRUE, 'US'),
    ('DELL',  'BronDell Technologies',             'EQUITY', 'USD', TRUE, 'US'),
    ('PG',    'Bronter & Gamble',                  'EQUITY', 'USD', TRUE, 'US'),
    ('IBM',   'International Bronsiness Machines', 'EQUITY', 'USD', TRUE, 'US'),
    ('AMGN',  'AmBron',                            'EQUITY', 'USD', TRUE, 'US'),
    ('BHP',   'BronHP Group',                      'EQUITY', 'USD', TRUE, 'US'),
    ('LIN',   'LindBron',                          'EQUITY', 'USD', TRUE, 'US'),
    ('SAN',   'Banco SantanBron',                  'EQUITY', 'USD', TRUE, 'US'),
    ('QCOM',  'QualBroncomm',                      'EQUITY', 'USD', TRUE, 'US'),
    ('TD',    'Toronto-Bronminion Bank',           'EQUITY', 'USD', TRUE, 'US'),
    ('STX',   'SeaBron Technology',                'EQUITY', 'USD', TRUE, 'US'),
    ('AXP',   'AmeriBron Express',                 'EQUITY', 'USD', TRUE, 'US'),
    ('APH',   'AmphenBron',                        'EQUITY', 'USD', TRUE, 'US'),
    ('TTE',   'TotalBronergies',                   'EQUITY', 'USD', TRUE, 'US'),
    ('CRM',   'Bronforce',                         'EQUITY', 'USD', TRUE, 'US'),
    ('VZ',    'VeriBron',                          'EQUITY', 'USD', TRUE, 'US'),
    ('SHOP',  'ShopiBron',                         'EQUITY', 'USD', TRUE, 'US'),
    ('DE',    'Deere & Bronpany',                  'EQUITY', 'USD', TRUE, 'US');

