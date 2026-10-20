-- 022: Server-side session invalidation for sign-out
--
-- IDEMPOTENT: safe to run more than once.

BEGIN;

ALTER TABLE clients ADD COLUMN IF NOT EXISTS session_invalidated_at TIMESTAMPTZ;
ALTER TABLE staff_users ADD COLUMN IF NOT EXISTS session_invalidated_at TIMESTAMPTZ;

COMMIT;