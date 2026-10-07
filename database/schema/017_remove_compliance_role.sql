-- LMKT-142: Remove COMPLIANCE role. Compliance duties move to TRADING_OPS.
-- The enum and frontend type were removed before this migration. Seed data must not reference
-- COMPLIANCE after this migration runs.

BEGIN;

-- Remove the compliance seed user (will violate the CHECK if migration runs after 011).
DELETE FROM staff_users WHERE role = 'COMPLIANCE';

-- The constraint was defined inline (unnamed) in 010. Postgres names it staff_users_role_check.
ALTER TABLE staff_users DROP CONSTRAINT staff_users_role_check;

-- Re-add with only TRADING_OPS and ANALYST (the only valid staff roles now).
ALTER TABLE staff_users ADD CONSTRAINT staff_users_role_check
    CHECK (role IN ('TRADING_OPS', 'ANALYST'));

COMMIT;
