-- 016: Audit lockdown (LMKT-100, contracts/C2-audit.md)
--
-- An audit record never changes once it is written. The database enforces that in two
-- independent layers, on audit_log and on order_events (001's unused draft, locked as well so it
-- cannot quietly be put to use):
--
--   1. lemarket_app - the account the services log in with. It may only SELECT and INSERT on the
--                     audit tables, so its UPDATE, DELETE or TRUNCATE fails with "permission
--                     denied" (SQLSTATE 42501). It owns nothing, so it cannot grant itself more.
--   2. triggers     - refuse UPDATE, DELETE and TRUNCATE on the audit tables for every role, the
--                     owner included (SQLSTATE LM001). They catch a mistaken grant, and a
--                     migration that would rewrite history.
--
-- Run as the owner, lemarket, like every other file. lemarket keeps running migrations, seeding
-- and resets; only the services change account.
--
-- Needs psql: it reads the new role's password from the APP_DB_PASSWORD environment variable of
-- the psql process (\getenv), and falls back to the development default below.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on). The row
-- triggers fire per row, so the old backfills in 009, 010 and 015 still pass when re-applied:
-- by then they match no rows.

-- ---------------------------------------------------------------------------
-- 1. The application account
-- ---------------------------------------------------------------------------
\getenv app_password APP_DB_PASSWORD
\if :{?app_password}
\else
    \set app_password changeme_app
\endif

-- Where lemarket is not a superuser (a native install, see scripts/windows/setup-db.ps1) it
-- cannot create roles, so the role has to exist already. Say so instead of failing obscurely.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lemarket_app')
       AND NOT (SELECT rolsuper OR rolcreaterole FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Role lemarket_app does not exist and % may not create it', current_user
            USING HINT = 'Create it as a superuser first; see database/README.md (016).';
    END IF;
END $$;

BEGIN;

-- Keep the new role's password out of the server log, whatever statement logging is switched on.
-- Only a superuser may change these settings, and only a superuser reaches the CREATE ROLE below.
SELECT rolsuper AS is_superuser FROM pg_roles WHERE rolname = current_user \gset
\if :is_superuser
    SET LOCAL log_statement = 'none';
    SET LOCAL log_min_error_statement = 'panic';
\endif

SELECT format('CREATE ROLE lemarket_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
              :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lemarket_app')
\gexec

COMMIT;

BEGIN;

-- ---------------------------------------------------------------------------
-- 2. What the application account may do
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO lemarket_app;

-- Ordinary read and write on everything that exists now...
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO lemarket_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO lemarket_app;
-- ...and on whatever a later migration creates, so a new table needs no grant of its own.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO lemarket_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO lemarket_app;

-- The audit tables are the exception: insert and read only. Revoked after the blanket grant above
-- and in the same transaction, so there is no moment at which the account holds more.
REVOKE ALL ON audit_log, order_events FROM lemarket_app;
GRANT SELECT, INSERT ON audit_log, order_events TO lemarket_app;

-- ---------------------------------------------------------------------------
-- 3. The second layer: nobody changes an audit record, whatever they were granted
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION audit_refuse_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- LM001 is this project's own code, so a caller can tell this refusal from a missing privilege.
    RAISE EXCEPTION 'Audit records are immutable: % on % refused', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'LM001',
              HINT = 'Audit records are never updated or deleted; see contracts/C2-audit.md.';
END $$;

-- Per row for UPDATE and DELETE, so a statement that touches no audit record is not an error.
CREATE OR REPLACE TRIGGER audit_log_immutable_row
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_refuse_change();
CREATE OR REPLACE TRIGGER audit_log_immutable_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_refuse_change();

CREATE OR REPLACE TRIGGER order_events_immutable_row
    BEFORE UPDATE OR DELETE ON order_events
    FOR EACH ROW EXECUTE FUNCTION audit_refuse_change();
CREATE OR REPLACE TRIGGER order_events_immutable_truncate
    BEFORE TRUNCATE ON order_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_refuse_change();

-- ALWAYS: they also fire when session_replication_role is 'replica', which switches ordinary
-- triggers off. Stated on every run, so re-applying this file also re-arms a trigger that was
-- left disabled.
ALTER TABLE audit_log    ENABLE ALWAYS TRIGGER audit_log_immutable_row;
ALTER TABLE audit_log    ENABLE ALWAYS TRIGGER audit_log_immutable_truncate;
ALTER TABLE order_events ENABLE ALWAYS TRIGGER order_events_immutable_row;
ALTER TABLE order_events ENABLE ALWAYS TRIGGER order_events_immutable_truncate;

COMMIT;
