-- 015: Seed data for the submission audit trail (LMKT-99, contracts/C2-audit.md, contracts/C3-seed-data.md)
--
-- Brings the audit events seeded by 011 to the shape buy-sell-service writes since 014, so the
-- trade timeline can be built on seed data:
--
--   1. seeded SUBMITTED / VALIDATED events - get their submission's request_id and event_key;
--                                            SUBMITTED gains instrumentId, VALIDATED lists the
--                                            rules that ran
--   2. RULE_CHECKED                        - one passed check per rule for every seeded order that
--                                            was validated
--   3. a refused submission                - seed_notrading's refused BUY: a trail with no order
--
-- A separate file, not an edit to 011: 011 runs before 014 adds the columns used here, and is
-- skipped on databases that already hold the seed.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).
-- Requires 011 (the seed) and 014 (request_id, event_key, RULE_CHECKED).

BEGIN;

-- The placement rules, in the order they run. Mirrors buy-sell-service's ValidationRule; written
-- once here and used by both steps below.
CREATE TEMP TABLE seed_rules (rule VARCHAR(30), side VARCHAR(4), ord INTEGER) ON COMMIT DROP;
INSERT INTO seed_rules VALUES
    ('ACCOUNT_ACCESS',  'ANY',  1),
    ('ACCOUNT_STATUS',  'ANY',  2),
    ('LOCATION',        'ANY',  3),
    ('TRADABLE',        'ANY',  4),
    ('HOLDINGS',        'SELL', 5),
    ('PRICE_AVAILABLE', 'BUY',  5),
    ('QUOTE_FRESH',     'BUY',  6),
    ('CASH',            'BUY',  7);

-- ---------------------------------------------------------------------------
-- 1. Seeded SUBMITTED and VALIDATED events. The request ID is derived from the order ID, so it is
--    the same on every run; real ones are random UUIDs issued per request.
-- ---------------------------------------------------------------------------
UPDATE audit_log l
SET request_id = md5('seed-submission-' || o.order_id)::uuid::text,
    event_key  = md5('seed-submission-' || o.order_id)::uuid::text || ':' || l.action,
    details    = CASE l.action
                     WHEN 'SUBMITTED' THEN l.details || jsonb_build_object('instrumentId', o.instrument_id)
                     ELSE jsonb_build_object('checks',
                              (SELECT jsonb_agg(r.rule ORDER BY r.ord) FROM seed_rules r
                               WHERE r.side IN ('ANY', o.order_type)))
                 END
FROM orders o
JOIN accounts a ON a.account_id = o.account_id
JOIN clients c  ON c.client_id = a.client_id
WHERE l.order_id = o.order_id
  AND l.action IN ('SUBMITTED', 'VALIDATED')
  AND l.request_id IS NULL
  AND c.username LIKE 'seed\_%';

-- ---------------------------------------------------------------------------
-- 2. One passed check per rule, a tenth of a second apart, between SUBMITTED and VALIDATED (which
--    011 placed a second apart). They are inserted after the order's later events, so read a
--    seeded trail by created_at, not audit_id.
-- ---------------------------------------------------------------------------
INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at, request_id, event_key)
SELECT v.order_id, v.account_id, v.client_id, 'RULE_CHECKED',
       jsonb_build_object('rule', r.rule, 'result', 'PASS'),
       s.created_at + r.ord * INTERVAL '100 milliseconds',
       v.request_id,
       v.request_id || ':RULE_CHECKED:' || r.rule
FROM audit_log v
JOIN audit_log s  ON s.request_id = v.request_id AND s.action = 'SUBMITTED'
JOIN orders o     ON o.order_id = v.order_id
JOIN accounts a   ON a.account_id = o.account_id
JOIN clients c    ON c.client_id = a.client_id
JOIN seed_rules r ON r.side IN ('ANY', o.order_type)
WHERE v.action = 'VALIDATED'
  AND c.username LIKE 'seed\_%'
ORDER BY v.order_id, r.ord
ON CONFLICT (event_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. A refused submission: seed_notrading tried to buy with trading disabled on the account. A
--    refused order saves no order row, so the trail has no order_id and is found by request_id.
-- ---------------------------------------------------------------------------
INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at, request_id, event_key)
SELECT NULL, a.account_id, a.client_id, s.action,
       CASE s.action
           WHEN 'SUBMITTED' THEN jsonb_build_object('side', 'BUY', 'quantity', 5, 'price', NULL,
                                                    'instrumentId', i.instrument_id)
           ELSE jsonb_strip_nulls(jsonb_build_object('rule', s.rule, 'result', s.result, 'reason', s.reason))
       END,
       NOW() - INTERVAL '2 days' + s.step * INTERVAL '100 milliseconds',
       q.request_id,
       q.request_id || ':' || s.action || COALESCE(':' || s.rule, '')
FROM clients c
JOIN accounts a    ON a.client_id = c.client_id
JOIN instruments i ON i.ticker = 'KO'
CROSS JOIN (SELECT md5('seed-refused-submission')::uuid::text AS request_id) q
CROSS JOIN (VALUES
        ('SUBMITTED',    NULL::VARCHAR,    NULL::VARCHAR, NULL::VARCHAR,        0),
        ('RULE_CHECKED', 'ACCOUNT_ACCESS', 'PASS',        NULL,                 1),
        ('RULE_CHECKED', 'ACCOUNT_STATUS', 'FAIL',        'ACCOUNT_RESTRICTED', 2)
     ) AS s (action, rule, result, reason, step)
WHERE c.username = 'seed_notrading'
ORDER BY s.step
ON CONFLICT (event_key) DO NOTHING;

COMMIT;
