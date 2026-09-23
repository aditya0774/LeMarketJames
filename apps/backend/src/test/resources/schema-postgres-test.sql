-- Test database schema reset: clear test data before reinitializing.
-- Spring Boot runs schema-*.sql files BEFORE data.sql, allowing clean state for each test run.
-- This ensures no duplicate key errors from previous test runs on persistent PostgreSQL.

DELETE FROM instrument_market_params;
DELETE FROM audit_log;
DELETE FROM order_events;
DELETE FROM orders;
DELETE FROM holdings;
DELETE FROM accounts;
DELETE FROM addresses;
DELETE FROM clients;
DELETE FROM instruments;
