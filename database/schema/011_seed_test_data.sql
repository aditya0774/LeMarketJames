-- 011: Seed / test data set (LMKT-8, C3)
--
-- The personas below, and what each one is for, are described only in contracts/C3-seed-data.md.
-- Every seed login (clients and staff) uses the password  Pass123!  (the BCrypt hash below).
-- Dates are relative to NOW() so "this week", "last 12 months" and "two years ago" stay true.
--
-- IDEMPOTENT: the whole file is skipped once the marker client seed_active exists, so re-running it
-- (Jenkins re-applies every file from 004 on) never duplicates orders or doubles cash.
-- Requires 010 (staff_users, segment, lockout columns, the suspended stock CAVS).

DO $$
DECLARE
    -- BCrypt of Pass123!, generated with Spring's BCryptPasswordEncoder (what auth-service uses).
    pw CONSTANT TEXT := '$2a$10$WVIIRTi27akAPNomrusCd.9ux45N0agz/IjGQJ0ZGMNv2VkrFAPwS';
BEGIN
    IF EXISTS (SELECT 1 FROM clients WHERE username = 'seed_active') THEN
        RETURN;
    END IF;

    -- -----------------------------------------------------------------------
    -- Clients. SSNs are stored hashed at registration, so seed rows reuse the password hash.
    -- -----------------------------------------------------------------------
    INSERT INTO clients (username, password, email, full_name, date_of_birth, phone, registered_date,
                         ssn, employment_status, account_status, investment_experience, segment,
                         failed_login_attempts, locked_until)
    VALUES
        ('seed_active',    pw, 'seed_active@seed.lemarket.com',    'Ada Active',     '1988-04-12', '(555) 010-0001', NOW() - INTERVAL '3 years',  pw, 'EMPLOYED',   'ACTIVE',    'beginner',    'RETAIL',         0, NULL),
        ('seed_trader',    pw, 'seed_trader@seed.lemarket.com',    'Theo Trader',    '1992-09-30', '(555) 010-0002', NOW() - INTERVAL '2 years',  pw, 'SELF_EMPLOYED', 'ACTIVE', 'experienced', 'ACTIVE_TRADER',  0, NULL),
        ('seed_hnw',       pw, 'seed_hnw@seed.lemarket.com',       'Helena Wealth',  '1970-01-22', '(555) 010-0003', NOW() - INTERVAL '4 years',  pw, 'RETIRED',    'ACTIVE',    'experienced', 'HIGH_NET_WORTH', 0, NULL),
        ('seed_closed',    pw, 'seed_closed@seed.lemarket.com',    'Carl Closed',    '1985-06-05', '(555) 010-0004', NOW() - INTERVAL '5 years',  pw, 'EMPLOYED',   'CLOSED',    'beginner',    'RETAIL',         0, NULL),
        ('seed_expired',   pw, 'seed_expired@seed.lemarket.com',   'Eve Expired',    '1990-11-17', '(555) 010-0005', NOW() - INTERVAL '3 years',  pw, 'STUDENT',    'EXPIRED',   'beginner',    'RETAIL',         0, NULL),
        ('seed_locked',    pw, 'seed_locked@seed.lemarket.com',    'Liam Locked',    '1995-02-28', '(555) 010-0006', NOW() - INTERVAL '1 year',   pw, 'EMPLOYED',   'ACTIVE',    'beginner',    'RETAIL',         3, '2999-01-01'),
        ('seed_notrading', pw, 'seed_notrading@seed.lemarket.com', 'Nora NoTrading', '1983-08-08', '(555) 010-0007', NOW() - INTERVAL '2 years',  pw, 'UNEMPLOYED', 'ACTIVE',    'beginner',    'RETAIL',         0, NULL);

    INSERT INTO addresses (client_id, address_type, street_address, city, state, postal_code, country)
    SELECT client_id, 'RESIDENTIAL', '1 Seed Street', 'Akron', 'Ohio', '44308', 'US'
    FROM clients WHERE username LIKE 'seed\_%';

    -- Opening deposits; cash is recalculated from the filled orders further down.
    INSERT INTO accounts (client_id, cash_balance, currency, trading_enabled, opened_date)
    SELECT c.client_id, v.deposit, 'USD', v.trading_enabled, c.registered_date::date
    FROM (VALUES
        ('seed_active',     50000.00, TRUE),
        ('seed_trader',     25000.00, TRUE),
        ('seed_hnw',      1000000.00, TRUE),
        ('seed_closed',     10000.00, TRUE),
        ('seed_expired',    10000.00, TRUE),
        ('seed_locked',      5000.00, TRUE),
        ('seed_notrading',  10000.00, FALSE)
    ) AS v (username, deposit, trading_enabled)
    JOIN clients c ON c.username = v.username;

    -- -----------------------------------------------------------------------
    -- Staff users (one per staff role)
    -- -----------------------------------------------------------------------
    INSERT INTO staff_users (username, email, password, full_name, role) VALUES
        ('seed_ops',        'ops@seed.lemarket.com',        pw, 'Olivia Ops',        'TRADING_OPS'),
        ('seed_analyst',    'analyst@seed.lemarket.com',    pw, 'Andy Analyst',      'ANALYST'),
        ('seed_compliance', 'compliance@seed.lemarket.com', pw, 'Carmen Compliance', 'COMPLIANCE')
    ON CONFLICT (username) DO NOTHING;

    -- -----------------------------------------------------------------------
    -- Orders: every status, spread over days, months and years.
    -- BUY orders carry the price captured at submission; SELL orders only get one when filled.
    -- -----------------------------------------------------------------------
    INSERT INTO orders (account_id, instrument_id, order_type, quantity, price_per_unit, order_status,
                        rejection_reason, submitted_at, accepted_at, filled_at, created_at, updated_at)
    SELECT a.account_id, i.instrument_id, v.side, v.qty, v.price, v.status, v.reason,
           NOW() - v.age,
           CASE WHEN v.status IN ('ACCEPTED', 'PENDING', 'DELAYED', 'FILLED') THEN NOW() - v.age + INTERVAL '2 seconds' END,
           CASE WHEN v.status = 'FILLED' THEN NOW() - v.age + INTERVAL '3 seconds' END,
           NOW() - v.age,
           NOW() - v.age
    FROM (VALUES
        -- seed_active: monthly fills over the last 12 months, a closed position from past years,
        -- and one open order in each non-final status
        ('seed_active', 'AAPL',  'BUY',  20::NUMERIC, 190.00::NUMERIC, 'FILLED',    NULL::VARCHAR, INTERVAL '12 months'),
        ('seed_active', 'MSFT',  'BUY',  10, 410.00, 'FILLED',    NULL, INTERVAL '11 months'),
        ('seed_active', 'NVDA',  'BUY',  15, 120.00, 'FILLED',    NULL, INTERVAL '10 months'),
        ('seed_active', 'GOOGL', 'BUY',  12, 165.00, 'FILLED',    NULL, INTERVAL '9 months'),
        ('seed_active', 'AAPL',  'SELL',  5, 205.00, 'FILLED',    NULL, INTERVAL '8 months'),
        ('seed_active', 'AMZN',  'BUY',   8, 185.00, 'FILLED',    NULL, INTERVAL '7 months'),
        ('seed_active', 'JPM',   'BUY',  10, 210.00, 'FILLED',    NULL, INTERVAL '6 months'),
        ('seed_active', 'NVDA',  'SELL',  5, 135.00, 'FILLED',    NULL, INTERVAL '5 months'),
        ('seed_active', 'KO',    'BUY',  30,  62.00, 'FILLED',    NULL, INTERVAL '4 months'),
        ('seed_active', 'TSLA',  'BUY',   6, 250.00, 'FILLED',    NULL, INTERVAL '3 months'),
        ('seed_active', 'MSFT',  'SELL',  4, 430.00, 'FILLED',    NULL, INTERVAL '2 months'),
        ('seed_active', 'AAPL',  'BUY',   5, 230.00, 'FILLED',    NULL, INTERVAL '1 month'),
        ('seed_active', 'V',     'BUY',  10, 250.00, 'FILLED',    NULL, INTERVAL '2 years'),
        ('seed_active', 'V',     'SELL', 10, 275.00, 'FILLED',    NULL, INTERVAL '18 months'),
        ('seed_active', 'META',  'BUY',   2, 600.00, 'SUBMITTED', NULL, INTERVAL '10 minutes'),
        ('seed_active', 'AMD',   'BUY',  10, 160.00, 'ACCEPTED',  NULL, INTERVAL '1 day'),
        ('seed_active', 'GOOGL', 'SELL',  3, NULL,   'ACCEPTED',  NULL, INTERVAL '2 days'),
        ('seed_active', 'COST',  'BUY',   1, 900.00, 'PENDING',   NULL, INTERVAL '3 days'),
        ('seed_active', 'WMT',   'BUY',  10,  95.00, 'DELAYED',   NULL, INTERVAL '4 days'),
        ('seed_active', 'TSLA',  'SELL', 50, NULL,   'REJECTED',  'INSUFFICIENT_HOLDINGS', INTERVAL '5 days'),
        ('seed_active', 'CAVS',  'BUY',   5,  42.00, 'REJECTED',  'NOT_TRADABLE',          INTERVAL '6 days'),
        ('seed_active', 'BRK-A', 'BUY',   1, 700000.00, 'REJECTED', 'INSUFFICIENT_CASH',   INTERVAL '3 months'),
        -- seed_trader: frequent trading
        ('seed_trader', 'TSLA',  'BUY',  20, 240.00, 'FILLED',    NULL, INTERVAL '6 months'),
        ('seed_trader', 'TSLA',  'SELL', 10, 260.00, 'FILLED',    NULL, INTERVAL '4 months'),
        ('seed_trader', 'NVDA',  'BUY',  30, 125.00, 'FILLED',    NULL, INTERVAL '3 months'),
        ('seed_trader', 'AMD',   'BUY',  25, 150.00, 'FILLED',    NULL, INTERVAL '1 month'),
        ('seed_trader', 'PLTR',  'BUY',  50,  30.00, 'SUBMITTED', NULL, INTERVAL '1 hour'),
        -- seed_hnw: large positions
        ('seed_hnw',    'LLY',   'BUY', 100, 800.00, 'FILLED',    NULL, INTERVAL '10 months'),
        ('seed_hnw',    'JNJ',   'BUY', 200, 155.00, 'FILLED',    NULL, INTERVAL '5 months'),
        ('seed_hnw',    'V',     'BUY', 100, 280.00, 'ACCEPTED',  NULL, INTERVAL '1 day'),
        -- seed_closed: a round trip before the account was closed
        ('seed_closed', 'KO',    'BUY',  20,  55.00, 'FILLED',    NULL, INTERVAL '3 years'),
        ('seed_closed', 'KO',    'SELL', 20,  58.00, 'FILLED',    NULL, INTERVAL '2 years'),
        -- seed_expired: an old position still held
        ('seed_expired', 'AAPL', 'BUY',   5, 180.00, 'FILLED',    NULL, INTERVAL '18 months'),
        -- seed_notrading: an old position, and an order refused because trading is disabled
        ('seed_notrading', 'KO', 'BUY',  10,  60.00, 'FILLED',    NULL, INTERVAL '1 year'),
        ('seed_notrading', 'KO', 'BUY',   5,  64.00, 'REJECTED',  'ACCOUNT_RESTRICTED', INTERVAL '7 days')
    ) AS v (username, ticker, side, qty, price, status, reason, age)
    JOIN clients c     ON c.username = v.username
    JOIN accounts a    ON a.client_id = c.client_id
    JOIN instruments i ON i.ticker = v.ticker;

    -- -----------------------------------------------------------------------
    -- Holdings and cash, derived from the fills so every balance is consistent with the history.
    -- Average cost is the quantity-weighted BUY price (sells don't change it).
    -- -----------------------------------------------------------------------
    INSERT INTO holdings (account_id, instrument_id, quantity, average_cost, last_updated)
    SELECT o.account_id, o.instrument_id,
           SUM(CASE WHEN o.order_type = 'BUY' THEN o.quantity ELSE -o.quantity END),
           ROUND(SUM(CASE WHEN o.order_type = 'BUY' THEN o.quantity * o.price_per_unit ELSE 0 END)
                 / NULLIF(SUM(CASE WHEN o.order_type = 'BUY' THEN o.quantity ELSE 0 END), 0), 4),
           MAX(o.filled_at)
    FROM orders o
    JOIN accounts a ON a.account_id = o.account_id
    JOIN clients c  ON c.client_id = a.client_id
    WHERE c.username LIKE 'seed\_%' AND o.order_status = 'FILLED'
    GROUP BY o.account_id, o.instrument_id
    HAVING SUM(CASE WHEN o.order_type = 'BUY' THEN o.quantity ELSE -o.quantity END) > 0;

    UPDATE accounts a
    SET cash_balance = a.cash_balance + f.net
    FROM (SELECT o.account_id,
                 SUM(CASE WHEN o.order_type = 'SELL' THEN 1 ELSE -1 END * o.quantity * o.price_per_unit) AS net
          FROM orders o
          WHERE o.order_status = 'FILLED'
          GROUP BY o.account_id) f
    JOIN clients c ON c.username LIKE 'seed\_%'
    WHERE a.account_id = f.account_id AND a.client_id = c.client_id;

    -- The closed account paid out its remaining cash when it closed.
    UPDATE accounts SET cash_balance = 0, trading_enabled = FALSE
    WHERE client_id = (SELECT client_id FROM clients WHERE username = 'seed_closed');

    -- -----------------------------------------------------------------------
    -- Audit events for every seeded order, one per lifecycle step it reached, a second apart.
    -- Detail keys match what the services write (see AuditEventType's Javadoc).
    -- -----------------------------------------------------------------------
    INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at)
    SELECT o.order_id, o.account_id, a.client_id, s.action,
           CASE s.action
               WHEN 'SUBMITTED' THEN jsonb_build_object('side', o.order_type, 'quantity', o.quantity, 'price', o.price_per_unit)
               WHEN 'VALIDATED' THEN jsonb_build_object('checks', CASE WHEN o.order_type = 'BUY'
                                         THEN jsonb_build_array('ACCOUNT', 'TRADABLE', 'CASH')
                                         ELSE jsonb_build_array('ACCOUNT', 'TRADABLE', 'HOLDINGS') END)
               WHEN 'ACCEPTED'  THEN '{}'::jsonb
               WHEN 'FILLED'    THEN jsonb_build_object('quantity', o.quantity, 'price', o.price_per_unit)
               WHEN 'SETTLED'   THEN jsonb_build_object('cashDelta',
                                         CASE WHEN o.order_type = 'BUY' THEN -1 ELSE 1 END * o.quantity * o.price_per_unit,
                                         'quantityDelta', CASE WHEN o.order_type = 'BUY' THEN o.quantity ELSE -o.quantity END)
               WHEN 'REJECTED'  THEN jsonb_build_object('reason', o.rejection_reason)
           END,
           o.submitted_at + s.step * INTERVAL '1 second'
    FROM orders o
    JOIN accounts a ON a.account_id = o.account_id
    JOIN clients c  ON c.client_id = a.client_id
    JOIN (VALUES ('SUBMITTED', 0), ('VALIDATED', 1), ('ACCEPTED', 2), ('FILLED', 3), ('SETTLED', 4), ('REJECTED', 1))
         AS s (action, step)
      ON (s.action = 'SUBMITTED')
      OR (s.action = 'VALIDATED' AND o.order_status <> 'REJECTED')
      OR (s.action = 'ACCEPTED'  AND o.order_status IN ('ACCEPTED', 'PENDING', 'DELAYED', 'FILLED'))
      OR (s.action IN ('FILLED', 'SETTLED') AND o.order_status = 'FILLED')
      OR (s.action = 'REJECTED'  AND o.order_status = 'REJECTED')
    WHERE c.username LIKE 'seed\_%';
END $$;
