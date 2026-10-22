-- 030: Nightly trade summary and overnight report outputs (BR-16, contracts/C6-api.md,
-- services/staff-ops-service/README.md "Read side")
--
-- reporting_daily_trades  - the separated read side for long-range reports: one row per trading day
--                           (in a reports time zone), stock, client segment and side, with the
--                           trades' count, shares and gross amount. Built only from the
--                           reporting_trades view (010), by the overnight "Monthly trading summary"
--                           job, so it holds aggregates and nothing that identifies a client.
-- reporting_summary_state - per time zone, the last day the summary covers. Every earlier day is
--                           covered too, including days with no trades, which have no rows. A
--                           report reads the summary up to that day and the live view after it.
-- report_outputs          - the file an overnight run produced (the monthly summary CSV), one per run,
--                           downloaded from the Analyst dashboard.
--
-- A day stays right once summarized: trades are final and never change (029), and a fill is stamped
-- with the moment it happens, so no trade lands on a day after that day has been summarized.
-- The time zone is part of the key, so changing lmj.reports.time-zone never mixes days of two zones:
-- the new zone has no summary until the next night builds it, and reports read the live view meanwhile.
--
-- staff-ops-service writes these tables through its reports pool, whose connections are read-only by
-- default; only its overnight writers open read-write transactions (README, rule 5). 016's default
-- privileges give lemarket_app read and write on them.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

CREATE TABLE IF NOT EXISTS reporting_daily_trades (
    -- IANA ID of the reports time zone the day is in (lmj.reports.time-zone).
    time_zone    VARCHAR(64) NOT NULL,
    trade_date   DATE NOT NULL,
    -- Types as in reporting_trades, so sums carry over exactly.
    symbol       VARCHAR(20) NOT NULL,
    segment      VARCHAR(20) NOT NULL,
    side         VARCHAR(10) NOT NULL,
    trade_count  INTEGER NOT NULL CHECK (trade_count > 0),
    quantity     NUMERIC NOT NULL,
    gross_amount NUMERIC NOT NULL,
    PRIMARY KEY (time_zone, trade_date, symbol, segment, side)
);

CREATE TABLE IF NOT EXISTS reporting_summary_state (
    time_zone          VARCHAR(64) PRIMARY KEY,
    summarized_through DATE NOT NULL,
    refreshed_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS report_outputs (
    -- One output per run. Removing a run (a test's cleanup) removes its output with it.
    run_id       UUID PRIMARY KEY REFERENCES report_runs(run_id) ON DELETE CASCADE,
    -- The file name the analyst downloads, e.g. monthly-trading-summary-2026-10-01-to-2026-10-20.csv.
    file_name    VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    body         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMIT;
