-- Test database schema reset: clear test data before reinitializing.
-- Spring Boot runs schema-*.sql files BEFORE data.sql, allowing clean state for each test run.
-- This ensures no duplicate key errors from previous test runs on persistent PostgreSQL.
-- Delete order must respect foreign key constraints (child tables before parents).

DELETE FROM audit_log;           -- FK: orders (order_id), accounts (account_id)
DELETE FROM order_events;        -- FK: orders (order_id)
DELETE FROM orders;              -- FK: accounts (account_id), instruments (instrument_id)
DELETE FROM holdings;            -- FK: accounts (account_id), instruments (instrument_id)
DELETE FROM market_quotes;       -- FK: instruments (instrument_id)
DELETE FROM instrument_market_params; -- FK: instruments (instrument_id)
DELETE FROM accounts;            -- FK: clients (client_id)
DELETE FROM addresses;           -- FK: clients (client_id)
DELETE FROM clients;             -- No FK dependencies
DELETE FROM instruments;         -- No FK dependencies
