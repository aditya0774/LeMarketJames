-- 022: Overnight report runs (LMKT-45, contracts/C6-api.md "GET /api/v1/reports/overnight")
--
-- reporting-service runs the heavy reports overnight and records one row per report per night:
-- whether it is waiting, running, ready, late or failed, when it was ready, and why it failed. The
-- Analyst dashboard lists these rows; the platform sends no notifications, so a failure is shown
-- only here.
--
-- This is the one table reporting-service writes. Its connections are read-only by default
-- (services/reporting-service/README.md, "Read-side safeguards"); only its run writer opens a
-- read-write transaction, and only for this table.
--
-- No foreign keys and no client data: a run is about a report, never about a client.
--
-- lemarket_app needs no grant here: 016 gives it read and write on every table created later.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

CREATE TABLE IF NOT EXISTS report_runs (
    run_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_name    VARCHAR(100) NOT NULL,
    -- The trading day the run covers, in the reports time zone (lmj.reports.time-zone).
    run_date       DATE NOT NULL,
    -- A RunStatus (reporting-service) as text. No CHECK: the list lives in code.
    status         VARCHAR(20) NOT NULL,
    scheduled_for  TIMESTAMPTZ NOT NULL,
    -- When a successful run must be ready by (lmj.reports.ready-by on that night).
    ready_by       TIMESTAMPTZ NOT NULL,
    started_at     TIMESTAMPTZ,
    -- When the run finished: set when it is READY, or LATE and finished after ready_by.
    ready_at       TIMESTAMPTZ,
    -- Set only when the run FAILED: a message an analyst can read, never a stack trace.
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- A report runs once a night, however often the scheduler is triggered or restarted.
    CONSTRAINT report_runs_report_night_key UNIQUE (report_name, run_date)
);
-- The list shows the most recent nights first.
CREATE INDEX IF NOT EXISTS report_runs_run_date_idx ON report_runs (run_date DESC);

COMMIT;
