-- LEAP Direct Trading Platform — Schema
-- Domain: self-directed retail trading platform
-- Key features: order lifecycle tracking, audit trail, market pricing, position management

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS order_events;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS market_quotes;
DROP TABLE IF EXISTS holdings;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS instruments;
DROP TABLE IF EXISTS clients;
DROP TABLE IF EXISTS addresses;

-- Clients: retail traders
CREATE TABLE clients (
    client_id       SERIAL PRIMARY KEY,
    username        VARCHAR(100) UNIQUE NOT NULL,
    password        VARCHAR(255) NOT NULL, -- hashed password
    email           VARCHAR(100) NOT NULL,
    full_name       TEXT NOT NULL,
    date_of_birth   DATE NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    registered_date TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login      TIMESTAMP,
    ssn            VARCHAR(11) UNIQUE, -- format: XXX-XX-XXXX
    employment_status VARCHAR(20) NOT NULL DEFAULT 'EMPLOYED'
                    CHECK (employment_status IN ('EMPLOYED', 'SELF_EMPLOYED', 'RETIRED', 'STUDENT', 'UNEMPLOYED', 'OTHER')),
    employer_name   VARCHAR(200),
    occupation      VARCHAR(100),
    account_status  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' 
                    CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

-- Addresses: residential and mailing addresses
CREATE TABLE addresses (
    address_id      SERIAL PRIMARY KEY,
    client_id       INTEGER NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    address_type    VARCHAR(20) NOT NULL CHECK (address_type IN ('RESIDENTIAL', 'MAILING')),
    street_address  VARCHAR(255) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(2) NOT NULL,
    postal_code     VARCHAR(10) NOT NULL,
    country         VARCHAR(2) NOT NULL DEFAULT 'US',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Accounts: trading accounts (one per client for MVP)
CREATE TABLE accounts (
    account_id      SERIAL PRIMARY KEY,
    client_id       INTEGER NOT NULL UNIQUE REFERENCES clients(client_id),
    cash_balance    NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    trading_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    opened_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Instruments: tradable assets (equities, forex, crypto)
CREATE TABLE instruments (
    instrument_id   SERIAL PRIMARY KEY,
    ticker          VARCHAR(20) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    asset_class     VARCHAR(20) NOT NULL 
                    CHECK (asset_class IN ('EQUITY', 'FOREX', 'CRYPTO')),
    currency        VARCHAR(3) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE,
    location        VARCHAR(50) -- e.g., 'US', 'INDIA', 'UK'
);

-- Market Quotes: current pricing (updated real-time)
CREATE TABLE market_quotes (
    quote_id        SERIAL PRIMARY KEY,
    instrument_id   INTEGER NOT NULL REFERENCES instruments(instrument_id),
    bid_price       NUMERIC(14,4) NOT NULL,
    ask_price       NUMERIC(14,4) NOT NULL,
    last_updated    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Orders: full order lifecycle (BR-06, BR-07)
CREATE TABLE orders (
    order_id        SERIAL PRIMARY KEY,
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id   INTEGER NOT NULL REFERENCES instruments(instrument_id),
    order_type      VARCHAR(10) NOT NULL 
                    CHECK (order_type IN ('BUY', 'SELL')),
    quantity        NUMERIC(14,4) NOT NULL,
    price_per_unit  NUMERIC(14,4), -- filled price, NULL if not filled yet
    order_status    VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED'
                    CHECK (order_status IN ('SUBMITTED', 'ACCEPTED', 'PENDING', 'FILLED', 'REJECTED', 'DELAYED')),
    rejection_reason VARCHAR(255),
    submitted_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMP,
    filled_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Order Events: audit trail (BR-14, BR-15)
CREATE TABLE order_events (
    event_id        SERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(order_id),
    event_type      VARCHAR(50) NOT NULL, -- SUBMITTED, ACCEPTED, FILLED, REJECTED, PENDING, DELAYED
    event_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    quote_used      JSONB, -- {bid_price, ask_price, quote_time}
    validation_results JSONB, -- {checks passed, cash available, etc}
    details         JSONB -- flexible for other event details
);

-- Holdings: current positions (BR-10)
CREATE TABLE holdings (
    holding_id      SERIAL PRIMARY KEY,
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id   INTEGER NOT NULL REFERENCES instruments(instrument_id),
    quantity        NUMERIC(14,4) NOT NULL,
    last_updated    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(account_id, instrument_id)
);

-- Audit Log: permanent legal record (7-year retention, BR-14)
CREATE TABLE audit_log (
    audit_id        BIGSERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(order_id),
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    action          VARCHAR(100) NOT NULL, -- ORDER_ACCEPTED, ORDER_FILLED, HOLDING_UPDATED, CASH_ADJUSTED
    old_values      JSONB,
    new_values      JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    archived        BOOLEAN NOT NULL DEFAULT FALSE
);

-- Seed data for testing (US STOCKS ONLY)
INSERT INTO instruments (ticker, name, asset_class, currency, tradable, location) VALUES
    ('AAPL',   'Apple Inc',           'EQUITY', 'USD', TRUE, 'US'),
    ('MSFT',   'Microsoft Corp',      'EQUITY', 'USD', TRUE, 'US'),
    ('GOOGL',  'Alphabet Inc',        'EQUITY', 'USD', TRUE, 'US');

INSERT INTO market_quotes (instrument_id, bid_price, ask_price, last_updated) VALUES
    (1, 227.50, 227.60, NOW()),
    (2, 429.80, 429.90, NOW()),
    (3, 175.25, 175.35, NOW());

INSERT INTO addresses (client_id, address_type, street_address, city, state, postal_code, country) VALUES
    (1, 'RESIDENTIAL', '123 Oak Street', 'San Francisco', 'CA', '94102', 'US'),
    (1, 'MAILING', '123 Oak Street', 'San Francisco', 'CA', '94102', 'US'),
    (2, 'RESIDENTIAL', '456 Pine Avenue', 'New York', 'NY', '10001', 'US'),
    (2, 'MAILING', '456 Pine Avenue', 'New York', 'NY', '10001', 'US'),
    (3, 'RESIDENTIAL', '789 Elm Boulevard', 'Austin', 'TX', '78701', 'US'),
    (3, 'MAILING', '789 Elm Boulevard', 'Austin', 'TX', '78701', 'US');

INSERT INTO clients (username, email, full_name, date_of_birth, phone, registered_date, ssn, employment_status, employer_name, occupation) VALUES
    ('joanna_trader',  'joanna@example.com',  'Joanna Smith',   '1990-03-15', '555-0101', NOW() - INTERVAL '6 months', '123-45-6789', 'EMPLOYED', 'TechCorp Inc', 'Software Engineer'),
    ('david_investor', 'david@example.com',   'David Chen',     '1985-07-22', '555-0102', NOW() - INTERVAL '3 months', '234-56-7890', 'SELF_EMPLOYED', 'Chen Consulting LLC', 'Business Consultant'),
    ('priya_analyst',  'priya@example.com',   'Priya Patel',    '1995-11-08', '555-0103', NOW() - INTERVAL '1 month', '345-67-8901', 'EMPLOYED', 'FinanceFlow Analytics', 'Data Analyst');

INSERT INTO accounts (client_id, cash_balance, currency, opened_date) VALUES
    (1, 50000.00, 'USD', CURRENT_DATE - INTERVAL '6 months'),
    (2, 100000.00, 'USD', CURRENT_DATE - INTERVAL '3 months'),
    (3, 75000.00, 'USD', CURRENT_DATE - INTERVAL '1 month');

INSERT INTO holdings (account_id, instrument_id, quantity, last_updated) VALUES
    (1, 1, 10.5, NOW()),      -- Joanna: 10.5 AAPL
    (1, 2, 5.0, NOW()),       -- Joanna: 5 MSFT
    (2, 3, 20.0, NOW()),      -- David: 20 GOOGL
    (3, 1, 15.0, NOW());      -- Priya: 15 AAPL

INSERT INTO orders (account_id, instrument_id, order_type, quantity, price_per_unit, order_status, accepted_at, filled_at) VALUES
    (1, 1, 'BUY', 5, 227.55, 'FILLED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    (1, 2, 'BUY', 3, 429.85, 'FILLED', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    (2, 3, 'BUY', 10, 175.30, 'FILLED', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours'),
    (3, 1, 'BUY', 8, 227.50, 'ACCEPTED', NOW() - INTERVAL '30 minutes', NULL);

INSERT INTO order_events (order_id, event_type, event_timestamp, quote_used, validation_results) VALUES
    (1, 'SUBMITTED', NOW() - INTERVAL '2 days 5 minutes', 
     '{"bid": 227.50, "ask": 227.60}', '{"cash_available": true, "tradable": true}'),
    (1, 'ACCEPTED', NOW() - INTERVAL '2 days 4 minutes', 
     '{"bid": 227.50, "ask": 227.60}', '{"all_checks": "passed"}'),
    (1, 'FILLED', NOW() - INTERVAL '2 days', 
     '{"bid": 227.50, "ask": 227.60}', '{"filled_at_price": 227.55}'),
    (4, 'SUBMITTED', NOW() - INTERVAL '30 minutes 5 seconds', 
     '{"bid": 227.50, "ask": 227.60}', '{"cash_available": true, "tradable": true}'),
    (4, 'ACCEPTED', NOW() - INTERVAL '30 minutes', 
     '{"bid": 227.50, "ask": 227.60}', '{"all_checks": "passed"}');

INSERT INTO audit_log (order_id, account_id, action, old_values, new_values) VALUES
    (1, 1, 'ORDER_ACCEPTED', '{"status": "SUBMITTED"}', '{"status": "ACCEPTED"}'),
    (1, 1, 'ORDER_FILLED', '{"status": "ACCEPTED", "quantity": 0}', '{"status": "FILLED", "quantity": 5}'),
    (1, 1, 'HOLDING_UPDATED', '{"AAPL": 5.5}', '{"AAPL": 10.5}'),
    (1, 1, 'CASH_ADJUSTED', '{"cash": 51137.75}', '{"cash": 50973.50}');