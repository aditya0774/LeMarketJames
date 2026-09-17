-- OP-07 tradability check seed
-- Keep one deterministic non-tradable instrument for CI and automated tests.
UPDATE instruments
SET tradable = FALSE
WHERE ticker = 'GOOGL';
