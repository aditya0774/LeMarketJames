-- 017: Remove COMPLIANCE role (LMKT-119, contracts/C7-roles.md)
--
-- The team has decided the platform has three roles: CLIENT, TRADING_OPS and ANALYST.
-- COMPLIANCE is redundant; compliance and risk duties belong to Trading Operations instead.
--
-- This migration:
--   1. Deletes the compliance seed row from staff_users (if it exists)
--   2. Drops the old role CHECK constraint (which includes COMPLIANCE)
--   3. Re-adds the constraint with TRADING_OPS and ANALYST only
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).
--
-- Note: The CHECK constraint is auto-named by Postgres as staff_users_role_check.
-- If your database names it differently, update the constraint name below accordingly.

BEGIN;

-- Delete the compliance seed row
DELETE FROM staff_users WHERE role = 'COMPLIANCE';

-- Drop the old CHECK constraint and re-add it without COMPLIANCE
-- The constraint is auto-named staff_users_role_check; update if your database uses a different name.
ALTER TABLE staff_users DROP CONSTRAINT IF EXISTS staff_users_role_check;
ALTER TABLE staff_users ADD CONSTRAINT staff_users_role_check
    CHECK (role IN ('TRADING_OPS', 'ANALYST'));

COMMIT;
