-- LMKT-43 proof-of-concept personas. Trades are created through the application by
-- scripts/windows/seed-segment-report.py so settlement and audit use their normal writers.
-- Reapplying never resets balances or changes existing clients.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('019_segment_report_demo_clients'));

CREATE TEMP TABLE segment_demo_clients ON COMMIT DROP AS
SELECT 'demo_segment_' || n AS username,
       CASE WHEN n <= 3 THEN 'seed_active' WHEN n <= 5 THEN 'seed_trader' ELSE 'seed_hnw' END AS source_username
FROM generate_series(1, 7) AS n;

DO $$
BEGIN
    IF (SELECT count(*) FROM clients WHERE username IN ('seed_active', 'seed_trader', 'seed_hnw')) <> 3 THEN
        RAISE EXCEPTION 'Apply 011 seed data before the segment report demo clients';
    END IF;
END $$;

-- Reuse the existing fictional seed personas' password hash and segment attribute.
-- Segment definitions remain owned by the original seed, not copied into this migration.
INSERT INTO clients (username, password, email, full_name, date_of_birth, phone, ssn,
                     employment_status, account_status, investment_experience, segment)
SELECT d.username, c.password, d.username || '@seed.lemarket.com',
       'Segment Demo ' || replace(d.username, 'demo_segment_', ''),
       c.date_of_birth, '(555) 010-0099', c.ssn,
       c.employment_status, 'ACTIVE', c.investment_experience, c.segment
FROM segment_demo_clients d JOIN clients c ON c.username = d.source_username
ON CONFLICT (username) DO NOTHING;

INSERT INTO accounts (client_id, cash_balance, currency, trading_enabled)
SELECT c.client_id, 1000000.00, 'USD', TRUE
FROM clients c JOIN segment_demo_clients d USING (username)
ON CONFLICT (client_id) DO NOTHING;

INSERT INTO addresses (client_id, address_type, street_address, city, state, postal_code, country)
SELECT c.client_id, 'RESIDENTIAL', '19 Demo Street', 'Akron', 'Ohio', '44308', 'US'
FROM clients c JOIN segment_demo_clients d USING (username)
WHERE NOT EXISTS (SELECT 1 FROM addresses a WHERE a.client_id = c.client_id AND a.address_type = 'RESIDENTIAL');
COMMIT;
