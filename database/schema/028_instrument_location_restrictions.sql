-- 028: Per-instrument location restrictions (BR-05, contracts/C5-config.md, contracts/C3-seed-data.md)
--
-- A row means: clients whose residential address is in `location` may not buy or sell this
-- instrument. `location` is matched, ignoring case, against addresses.state (the full US state name
-- the registration form writes, e.g. 'New Jersey') or addresses.country (an ISO code such as 'US').
-- The platform-wide list (PlatformSettings.Orders.restrictedLocations) still blocks every
-- instrument; this table narrows a restriction to the instruments it concerns.
--
-- Demo rule (D20): the six non-US companies' stocks are blocked for clients living in New Jersey,
-- with the persona seed_restricted living there to show it. No other seeded client lives in New
-- Jersey, so existing personas and tests are unaffected.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

CREATE TABLE IF NOT EXISTS instrument_location_restrictions (
    instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),
    location      VARCHAR(50) NOT NULL,
    PRIMARY KEY (instrument_id, location)
);

-- Rules are configuration, maintained by migrations: the services only read them.
REVOKE ALL ON instrument_location_restrictions FROM lemarket_app;
GRANT SELECT ON instrument_location_restrictions TO lemarket_app;

INSERT INTO instrument_location_restrictions (instrument_id, location)
SELECT instrument_id, 'New Jersey'
FROM instruments
WHERE ticker IN ('ASML', 'BHP', 'SAN', 'TD', 'TSM', 'TTE')
ON CONFLICT DO NOTHING;

-- seed_restricted: an active client in New Jersey with cash to trade. Same password as every seed
-- login (Pass123!, see 011); the SSN column reuses the hash as in 011.
INSERT INTO clients (username, password, email, full_name, date_of_birth, phone, registered_date,
                     ssn, employment_status, account_status, investment_experience, segment,
                     failed_login_attempts, locked_until)
VALUES ('seed_restricted',
        '$2a$10$WVIIRTi27akAPNomrusCd.9ux45N0agz/IjGQJ0ZGMNv2VkrFAPwS',
        'seed_restricted@seed.lemarket.com', 'Rita Restricted', '1987-03-14', '(555) 010-0008',
        NOW() - INTERVAL '1 year',
        '$2a$10$WVIIRTi27akAPNomrusCd.9ux45N0agz/IjGQJ0ZGMNv2VkrFAPwS',
        'EMPLOYED', 'ACTIVE', 'beginner', 'RETAIL', 0, NULL)
ON CONFLICT (username) DO NOTHING;

INSERT INTO addresses (client_id, address_type, street_address, city, state, postal_code, country)
SELECT c.client_id, 'RESIDENTIAL', '8 Seed Street', 'Trenton', 'New Jersey', '08608', 'US'
FROM clients c
WHERE c.username = 'seed_restricted'
  AND NOT EXISTS (SELECT 1 FROM addresses a WHERE a.client_id = c.client_id);

INSERT INTO accounts (client_id, cash_balance, currency, trading_enabled, opened_date)
SELECT c.client_id, 50000.00, 'USD', TRUE, c.registered_date::date
FROM clients c
WHERE c.username = 'seed_restricted'
ON CONFLICT (client_id) DO NOTHING;

COMMIT;
