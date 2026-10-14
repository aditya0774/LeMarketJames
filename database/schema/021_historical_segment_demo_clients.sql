-- Fictional historical-report personas; the companion Java seeder writes trades and
-- uses AuditRecorder for immutable audit events. Existing accounts are never reset.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('historical_segment_demo_2026'));
DO $$
BEGIN
    IF (SELECT count(*) FROM clients WHERE username IN ('seed_active', 'seed_trader', 'seed_hnw')) <> 3 THEN
        RAISE EXCEPTION 'Apply 011 before the historical segment demo';
    END IF;
END $$;
CREATE TEMP TABLE historical_demo_personas ON COMMIT DROP AS
SELECT n, 'demo_history_' || lpad(n::text, 2, '0') AS username,
       CASE WHEN n <= 18 THEN 'seed_active' WHEN n <= 26 THEN 'seed_trader' ELSE 'seed_hnw' END AS source_username,
       CASE WHEN n <= 18 THEN 50000 WHEN n <= 26 THEN 200000 ELSE 2000000 END AS deposit
FROM generate_series(1, 30) n;
INSERT INTO clients (username, password, email, full_name, date_of_birth, phone,
                     registered_date, ssn, employment_status, account_status, investment_experience, segment)
SELECT d.username, c.password, d.username || '@seed.lemarket.com',
       'Historical Demo ' || d.n, c.date_of_birth, '(555) 010-0088',
       TIMESTAMP '2026-05-01 12:00:00', c.ssn, c.employment_status,
       'ACTIVE', c.investment_experience, c.segment
FROM historical_demo_personas d JOIN clients c ON c.username = d.source_username
ON CONFLICT (username) DO NOTHING;
INSERT INTO accounts (client_id, cash_balance, currency, trading_enabled, opened_date, created_at, updated_at)
SELECT c.client_id, d.deposit, 'USD', TRUE, DATE '2026-05-01',
       TIMESTAMP '2026-05-01 12:00:00', TIMESTAMP '2026-05-01 12:00:00'
FROM historical_demo_personas d JOIN clients c USING (username)
ON CONFLICT (client_id) DO NOTHING;
INSERT INTO addresses (client_id, address_type, street_address, city, state, postal_code, country, created_at)
SELECT c.client_id, 'RESIDENTIAL', '21 Demo Street', 'Akron', 'Ohio', '44308', 'US',
       TIMESTAMP '2026-05-01 12:00:00'
FROM historical_demo_personas d JOIN clients c USING (username)
WHERE NOT EXISTS (SELECT 1 FROM addresses a WHERE a.client_id = c.client_id AND a.address_type = 'RESIDENTIAL');
COMMIT;
