-- PaySprint Wealth Platform — Enterprise Schema
-- Pre-loaded, read-heavy schema used for exploration and query practice
-- across Modules 02-05 (and referenced again in Module 10).
-- Domain: a wealth management platform. Advisors manage clients; clients
-- hold one or more accounts; accounts hold instruments via transactions
-- and current holdings.

DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS holdings;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS clients;
DROP TABLE IF EXISTS advisors;
DROP TABLE IF EXISTS instruments;

CREATE TABLE advisors (
    advisor_id   SERIAL PRIMARY KEY,
    name         TEXT NOT NULL,
    region       TEXT NOT NULL,
    hired_date   DATE NOT NULL
);

CREATE TABLE clients (
    client_id      SERIAL PRIMARY KEY,
    name           TEXT NOT NULL,
    date_of_birth  DATE NOT NULL,
    risk_profile   TEXT NOT NULL CHECK (risk_profile IN ('Cautious', 'Balanced', 'Adventurous')),
    advisor_id     INTEGER REFERENCES advisors(advisor_id),
    joined_date    DATE NOT NULL
);

CREATE TABLE instruments (
    instrument_id  SERIAL PRIMARY KEY,
    ticker         TEXT NOT NULL UNIQUE,
    name           TEXT NOT NULL,
    asset_class    TEXT NOT NULL CHECK (asset_class IN ('Equity', 'Bond', 'Fund', 'Cash')),
    currency       TEXT NOT NULL
);

CREATE TABLE accounts (
    account_id    SERIAL PRIMARY KEY,
    client_id     INTEGER NOT NULL REFERENCES clients(client_id),
    account_type  TEXT NOT NULL CHECK (account_type IN ('ISA', 'GIA', 'SIPP')),
    opened_date   DATE NOT NULL,
    currency      TEXT NOT NULL
);

CREATE TABLE holdings (
    holding_id     SERIAL PRIMARY KEY,
    account_id     INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id  INTEGER NOT NULL REFERENCES instruments(instrument_id),
    quantity       NUMERIC(14,4) NOT NULL,
    as_of_date     DATE NOT NULL
);

CREATE TABLE transactions (
    transaction_id  SERIAL PRIMARY KEY,
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id   INTEGER REFERENCES instruments(instrument_id),
    txn_type        TEXT NOT NULL CHECK (txn_type IN ('BUY', 'SELL', 'DIVIDEND', 'DEPOSIT', 'WITHDRAWAL')),
    quantity        NUMERIC(14,4),
    price           NUMERIC(14,4),
    txn_date        DATE NOT NULL
);

-- Seed data ------------------------------------------------------------

INSERT INTO advisors (name, region, hired_date) VALUES
    ('Priya Shah',       'London',     '2016-03-01'),
    ('Daniel Osei',      'Manchester', '2018-07-15'),
    ('Wei Zhang',        'Edinburgh',  '2015-01-20'),
    ('Fatima Al-Rashid', 'London',     '2020-09-10');

INSERT INTO instruments (ticker, name, asset_class, currency) VALUES
    ('VOD.L',  'Vodafone Group PLC',        'Equity', 'GBP'),
    ('BARC.L', 'Barclays PLC',              'Equity', 'GBP'),
    ('ULVR.L', 'Unilever PLC',              'Equity', 'GBP'),
    ('AAPL',   'Apple Inc',                 'Equity', 'USD'),
    ('GILT10', 'UK 10-Year Gilt',           'Bond',   'GBP'),
    ('CORPB1', 'Sterling Corporate Bond Fund', 'Fund', 'GBP'),
    ('GLBEQ1', 'Global Equity Index Fund',  'Fund',   'GBP'),
    ('CASHGBP','Cash (GBP)',                'Cash',   'GBP');

INSERT INTO clients (name, date_of_birth, risk_profile, advisor_id, joined_date) VALUES
    ('Alice Johnson',   '1978-04-12', 'Balanced',    1, '2019-02-01'),
    ('Brian Osei',      '1985-11-03', 'Adventurous', 2, '2020-06-15'),
    ('Carla Mendes',    '1962-08-22', 'Cautious',    1, '2017-11-01'),
    ('David Kim',       '1990-01-30', 'Adventurous', 3, '2021-03-10'),
    ('Elena Petrova',   '1971-06-18', 'Balanced',    2, '2018-09-05'),
    ('Farid Hossain',   '1988-12-09', 'Balanced',    4, '2022-01-20'),
    ('Grace Lin',       '1995-05-25', 'Adventurous', 3, '2023-04-01'),
    ('Harold Baxter',   '1955-02-14', 'Cautious',    1, '2015-07-01'),
    ('Isabel Marin',    '1980-09-09', 'Balanced',    4, '2019-10-12'),
    ('Jack Whitfield',  '1968-03-03', 'Cautious',    2, '2016-05-20'),
    ('Nadia Farouk',     '1993-07-19', 'Balanced',    3, '2026-06-01');
    -- Nadia is a newly onboarded prospective client: assigned an advisor, but
    -- hasn't opened an account yet. Deliberately included so LEFT JOIN /
    -- RIGHT JOIN produce NULLs for account columns, not just INNER JOIN
    -- results that happen to look the same as an inner join.

INSERT INTO accounts (client_id, account_type, opened_date, currency) VALUES
    (1, 'ISA',  '2019-02-01', 'GBP'),
    (1, 'GIA',  '2021-05-01', 'GBP'),
    (2, 'ISA',  '2020-06-15', 'GBP'),
    (3, 'SIPP', '2017-11-01', 'GBP'),
    (4, 'ISA',  '2021-03-10', 'GBP'),
    (4, 'GIA',  '2022-08-01', 'GBP'),
    (5, 'SIPP', '2018-09-05', 'GBP'),
    (6, 'ISA',  '2022-01-20', 'GBP'),
    (7, 'ISA',  '2023-04-01', 'GBP'),
    (8, 'SIPP', '2015-07-01', 'GBP'),
    (9, 'GIA',  '2019-10-12', 'GBP'),
    (10,'ISA',  '2016-05-20', 'GBP');

INSERT INTO holdings (account_id, instrument_id, quantity, as_of_date) VALUES
    (1, 3, 500,  '2026-06-30'), (1, 7, 1200, '2026-06-30'),
    (2, 4, 30,   '2026-06-30'),
    (3, 4, 15,   '2026-06-30'), (3, 8, 2000, '2026-06-30'),
    (4, 5, 5000, '2026-06-30'), (4, 6, 800,  '2026-06-30'),
    (5, 4, 60,   '2026-06-30'), (5, 1, 2000, '2026-06-30'),
    (6, 7, 3000, '2026-06-30'),
    (7, 5, 3000, '2026-06-30'), (7, 8, 1500, '2026-06-30'),
    (8, 4, 40,   '2026-06-30'), (8, 7, 900,  '2026-06-30'),
    (9, 5, 1000, '2026-06-30'),
    (10, 6, 1200, '2026-06-30'),
    (11, 2, 800,  '2026-06-30'), (11, 3, 600, '2026-06-30'),
    (12, 5, 6000, '2026-06-30'), (12, 8, 500, '2026-06-30');

INSERT INTO transactions (account_id, instrument_id, txn_type, quantity, price, txn_date) VALUES
    (1, 3, 'BUY',      500,  38.20, '2025-01-15'),
    (1, 7, 'BUY',      1200, 4.10,  '2025-02-01'),
    (1, 7, 'DIVIDEND', NULL, 45.00, '2025-08-01'),
    (2, 4, 'BUY',      30,   165.50,'2025-03-10'),
    (3, 4, 'BUY',      15,   150.00,'2020-01-05'),
    (3, 8, 'DEPOSIT',  NULL, 2000.00,'2020-01-05'),
    (4, 5, 'BUY',      5000, 0.98,  '2021-04-01'),
    (4, 6, 'BUY',      800,  5.25,  '2021-06-15'),
    (5, 4, 'BUY',      60,   140.00,'2019-01-10'),
    (5, 1, 'BUY',      2000, 1.15,  '2019-02-20'),
    (5, 1, 'DIVIDEND', NULL, 60.00, '2025-05-01'),
    (6, 7, 'BUY',      3000, 3.80,  '2022-02-01'),
    (7, 5, 'BUY',      3000, 0.97,  '2023-05-01'),
    (7, 8, 'DEPOSIT',  NULL, 1500.00,'2023-05-01'),
    (8, 4, 'BUY',      40,   130.00,'2015-08-01'),
    (8, 7, 'BUY',      900,  3.50,  '2016-01-15'),
    (8, 7, 'DIVIDEND', NULL, 30.00, '2025-08-01'),
    (9, 5, 'BUY',      1000, 0.99,  '2020-01-10'),
    (10,6, 'BUY',      1200, 5.00,  '2022-02-15'),
    (11,2, 'BUY',      800,  1.80,  '2020-01-15'),
    (11,3, 'BUY',      600,  36.00, '2020-03-01'),
    (12,5, 'BUY',      6000, 0.96,  '2016-06-01'),
    (12,8, 'DEPOSIT',  NULL, 500.00, '2016-06-01'),
    (1, 3, 'SELL',     100,  40.50, '2026-01-10'),
    (4, 6, 'SELL',     200,  5.60,  '2025-11-01'),
    (7, 5, 'SELL',     500,  1.02,  '2025-09-01'),
    (11,3, 'SELL',     100,  37.20, '2025-10-05'),
    (2, 4, 'DIVIDEND', NULL, 12.00, '2025-06-01'),
    (5, 4, 'SELL',     10,   170.00,'2026-02-01'),
    (12,5, 'DIVIDEND', NULL, 90.00, '2025-08-01');
