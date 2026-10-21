# database

Raw SQL, applied manually (no migration tool). `schema/` holds numbered, ordered SQL files (`001_core_schema.sql`, `002_...`) that together define the current database structure — apply them in order against the `lemarket` Postgres database. Add new numbered files here for future schema changes rather than editing old ones in place.

## One database, shared by every service

All backend microservices connect to this same `lemarket` database. This folder is the only place the schema is defined. Every service runs with `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate only checks the tables that service maps and never creates or changes them.

| Tables | Written by | Read by |
|---|---|---|
| `clients`, `addresses` | auth-service (registration, last login, lockout) | trading-service (profile, client status) |
| `staff_users` | seed data only (no staff admin yet) | auth-service (staff login) |
| `accounts` | auth-service (created at registration), trading-service (cash on settlement) | every servlet service |
| `instruments` | migrations only | orders-service (orders), trading-service (`GET /api/v1/instruments`, the market simulator) |
| `orders` | orders-service. Never deleted; a FILLED or REJECTED order never changes again ([029](#029--trade-record-lockdown)) | orders-service, trading-service (through orders-service's API) |
| `settlement_receipts` | trading-service. Insert only ([029](#029--trade-record-lockdown)) | trading-service (idempotent execution outcomes) |
| `holdings` | trading-service (settlement) | trading-service |
| `audit_log` | orders-service and trading-service, only through `AuditRecorder` ([C2](../contracts/C2-audit.md)). Insert only: nothing can update or delete a row ([016](#016--audit-lockdown)) | orders-service: the Trading Ops order timeline and the audit archive ([C6](../contracts/C6-api.md#audit-archive-trading_ops-only)) |
| `market_quotes`, `price_candles` | trading-service (market simulator) | trading-service |
| `price_history` | seed data ([022](#022--daily-price-history)), then trading-service (today's bar) | trading-service, for the stock chart and the portfolio chart ([C6](../contracts/C6-api.md#chart-ranges)) |
| `instrument_market_params` | migrations only | trading-service (market simulator) |
| `reporting_trades` (view) | — | staff-ops-service, as the only data source of its reports, through their read-only pool ([C6](../contracts/C6-api.md#reports-staff-ops-service)) |
| `notifications` | trading-service's notification consumer, from `OrderStatusChanged` events on Kafka ([018](#018--tables-of-the-kafka-consumers)) | trading-service |
| `order_alerts` | staff-ops-service's surveillance consumer, from `OrderSubmitted` and `OrderStatusChanged` events on Kafka ([019](#019--one-alert-per-order-and-reason)) | staff-ops-service |
| `trade_activity` | trading-service's market-activity consumer, from `OrderFilled` events on Kafka | trading-service |

The shared JPA mappings for `clients`/`addresses`/`accounts`/`staff_users`/`audit_log` live in `libs/common` (`com.lemarketjames.common.domain` and `.audit`). When a feature is extracted into its own service, update this table so it's clear who owns each write.

Existing Docker volumes do not rerun initialization scripts. For an existing database
that already has scripts 001–003, apply the SSN hash column update without deleting data:

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/004_widen_ssn_for_hash.sql
```

The command above uses a POSIX shell. In PowerShell:

```powershell
Get-Content database/schema/004_widen_ssn_for_hash.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket
```

Jenkins applies this repeatable update before its HTTP smoke tests. New databases
apply it automatically with the other initialization scripts.

## 006 — Market simulation

`006_market_simulation.sql` adds the tables used by the backend's simulated stock market
(`com.lemarketjames.market`, contract in [contracts/C4-quote-feed.md](../contracts/C4-quote-feed.md)):

| Table | Change | Purpose |
|---|---|---|
| `instruments` | Adds AMZN, TSLA, NVDA (ids 4–6) | Every quoted symbol is also a tradable instrument. Ids 1–3 are unchanged. |
| `instrument_market_params` | New, one row per instrument | How each price moves: initial price, drift, volatility, market correlation, bid/ask spread, shares outstanding, average daily volume, EPS, dividend. |
| `market_quotes` | Now one row per instrument; adds last/open/high/low/previous close/volume | Latest price snapshot, refreshed every few seconds so prices survive a restart. |
| `price_candles` | New | 1-minute OHLC price history. |

**trading-service will not start until 006 is applied** (Hibernate schema validation fails on the
missing tables). The script is idempotent, so it is safe to run again if you are unsure.

Apply to an existing Docker database without losing data:

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/006_market_simulation.sql
```

```powershell
Get-Content database/schema/006_market_simulation.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket
```

Or, if Postgres runs outside Docker:

```sh
psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket -f database/schema/006_market_simulation.sql
```

Alternatively `docker compose down -v && docker compose up -d` rebuilds the database from all
scripts, **deleting any local data**. With a native Windows PostgreSQL install (README Method 1),
`.\scripts\windows\setup-db.ps1 -Reset` does the same.

Verify:

```sh
psql -h localhost -U lemarket -d lemarket -c "SELECT i.ticker, p.initial_price, p.volatility FROM instruments i JOIN instrument_market_params p USING (instrument_id);"
```

### Tuning the market

Simulation parameters are plain data. For example, to make TSLA more volatile:

```sql
UPDATE instrument_market_params SET volatility = 0.70
WHERE instrument_id = (SELECT instrument_id FROM instruments WHERE ticker = 'TSLA');
```

Restart trading-service to pick up changes. `initial_price` only applies when an instrument has no
row in `market_quotes`; delete that row to restart an instrument from its initial price.

To add an instrument to the market, insert it into `instruments` and add a matching
`instrument_market_params` row in a new numbered migration. Instruments without params are not
quoted.

## 007 — LeBronified names

`007_lebronify_instruments.sql` gives every company a LeBron-themed display name and adds two
stocks. Tickers and ids are unchanged, so orders and holdings are unaffected, and GOOGL stays
non-tradable. **Superseded by 009**, which sets the final names, removes LMT and makes GOOGL
tradable; the table below shows the state after 007 only.

| Id | Ticker | Name |
|---|---|---|
| 1 | AAPL | AppLe Inc |
| 2 | MSFT | MicroBron Corp |
| 3 | GOOGL | Alphabron Inc (non-tradable) |
| 4 | AMZN | Amabron.com Inc |
| 5 | TSLA | TesLe Inc |
| 6 | NVDA | Nvidibron Corp |
| 7 | LMT | LeBronHeed Martin Corp (new) |
| 8 | AVGO | Broncom Inc (new) |

Apply to an existing Docker database (idempotent), then restart the backend so the simulator
picks up the new instruments:

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/007_lebronify_instruments.sql
docker compose restart backend
```

## 009 — The full 50-stock market

`009_full_lebron_market.sql` replaces the placeholder line-up with the final market: 50 LeBronified
parodies of real companies, **all tradable**.

| Change | Detail |
|---|---|
| LMT removed | Deleted along with every row that references it (`audit_log`, `order_events`, `orders`, `holdings`, `price_candles`, `market_quotes`, `instrument_market_params`). Id 7 is left unused. |
| 7 tickers kept | AAPL, MSFT, GOOGL, AMZN, TSLA, NVDA, AVGO keep their ids and get their final names. GOOGL becomes tradable (reverses 005). |
| 43 tickers added | Explicit ids 9–51, so every database agrees on them. |
| Market params | Upserted for all 50 with roughly realistic 2026 values. Existing `market_quotes` snapshots are kept, so live prices continue from where they are. |

Non-US companies (TSM, ASML, BHP, SAN, TD, TTE, SHOP) use their US listing/ADR: USD, US market
hours. Every stock is priced well under the $5,000 minimum deposit (the most expensive are about
$950), so any new account can buy whole shares of anything. BRK-A is therefore priced like
Berkshire's class B share (about $490 rather than the real class A's ~$730k), with shares
outstanding and EPS rescaled so its market cap and P/E are unchanged.

Nothing in the 50-stock real market is non-tradable any more. The suspended stock `CAVS` (added by
010, see [C3](../contracts/C3-seed-data.md)) is what the Jenkins smoke test uses for the
`NOT_TRADABLE` rejection; `SellOrderIntegrationTest` still creates (and deletes) its own fixture.

If 009 fails with a duplicate key on `instruments_pkey`, an earlier run of the `postgres-test`
integration tests left an instrument (ticker `IA…`) at an id between 9 and 51. The transaction rolls
back cleanly; delete those leftover test instruments and their orders, then apply 009 again.

| Id | Ticker | Name |
|---|---|---|
| 1 | AAPL | BronApple |
| 2 | MSFT | Bronisoft |
| 3 | GOOGL | AlphaBron |
| 4 | AMZN | AkronZon |
| 5 | TSLA | TesLe |
| 6 | NVDA | BronVidia |
| 8 | AVGO | Broncom |
| 9 | TSM | Taiwan SemiBronductor |
| 10 | META | MetaBron Platforms |
| 11 | MU | MicBron Technology |
| 12 | LLY | eLe Bronny |
| 13 | BRK-A | Bronshire Hathaway |
| 14 | AMD | Advanced Micro Bronvices |
| 15 | JPM | JPBron Chase |
| 16 | WMT | BronMart |
| 17 | V | VisaBron |
| 18 | ASML | ASBron Holding |
| 19 | XOM | Exxon MoBron |
| 20 | JNJ | Bronson & Bronson |
| 21 | INTC | BronTel |
| 22 | MA | MasterBronCard |
| 23 | ABBV | BronVie |
| 24 | ORCL | Bronacle |
| 25 | PLTR | PalanBron |
| 26 | CSCO | BronCisco Systems |
| 27 | CVX | CheBron |
| 28 | COST | CostBronco |
| 29 | BAC | Bank of Akron |
| 30 | LRCX | LamBron Research |
| 31 | KO | Coca-Bronla |
| 32 | AMAT | Applied Bronterials |
| 33 | CAT | CaterBron |
| 34 | MRK | Merck & Bron |
| 35 | DELL | BronDell Technologies |
| 36 | PG | Bronter & Gamble |
| 37 | IBM | International Bronsiness Machines |
| 38 | AMGN | AmBron |
| 39 | BHP | BronHP Group |
| 40 | LIN | LindBron |
| 41 | SAN | Banco SantanBron |
| 42 | QCOM | QualBroncomm |
| 43 | TD | Toronto-Bronminion Bank |
| 44 | STX | SeaBron Technology |
| 45 | AXP | AmeriBron Express |
| 46 | APH | AmphenBron |
| 47 | TTE | TotalBronergies |
| 48 | CRM | Bronforce |
| 49 | VZ | VeriBron |
| 50 | SHOP | ShopiBron |
| 51 | DE | Deere & Bronpany |

Apply to an existing Docker database (idempotent). Stop trading-service first: it holds LMT in
memory and would try to write LMT's price back after the delete.

```sh
docker compose stop trading-service
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/009_full_lebron_market.sql
docker compose start trading-service
```

```powershell
docker compose stop trading-service
Get-Content database/schema/009_full_lebron_market.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket
docker compose start trading-service
```

To restart a carried-over stock from its new `initial_price` instead of its stored price, delete its
snapshot (with trading-service stopped):

```sql
DELETE FROM market_quotes
WHERE instrument_id = (SELECT instrument_id FROM instruments WHERE ticker = 'NVDA');
```

## 010 — Shared contracts, 011 — Seed data set

`010_shared_contracts.sql` adds what the shared contracts need: staff logins, client segment and
persistent lockout, the audit event format, the suspended stock `CAVS` and the `reporting_trades`
view. `011_seed_test_data.sql` is the seed data set. What they are for is described in
[contracts/](../contracts/README.md) (C2, C3, C5, C6, C7); the test logins are in
[C3](../contracts/C3-seed-data.md).

Both are idempotent. Apply them to an existing database without losing data (restart
trading-service afterwards so it picks up `CAVS`):

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/010_shared_contracts.sql
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/011_seed_test_data.sql
docker compose restart trading-service
```

With a native Windows PostgreSQL install, `.\scripts\windows\setup-db.ps1 -Reset` rebuilds
everything including both.

## 012 — Durable order execution

Apply `schema/012_order_execution.sql` before starting the updated orders- and trading-service.
It adds the order's durable settlement flag and the settlement receipt table. Existing orders
start with no intent; open orders are picked up by the execution worker. Final orders are untouched.
Back up an existing database and stop the old order writer before switching to orders-service.

```powershell
Get-Content database/schema/012_order_execution.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. Receipts deliberately
have no order foreign key, preserving idempotency even after order cleanup. Never delete receipts
while their order IDs could be retried or reused. Cash updates and receipts commit together;
all settlements acquire the account lock first to serialize concurrent buys and sells.

## 013 — Quote used for a fill

Apply `schema/013_execution_quote.sql` before starting the updated orders-service (it runs with
`ddl-auto=validate` and will not start without the columns). It adds `orders.quote_source` and
`orders.quote_time`: the feed and the time of the quote an order was priced from at execution
([C6](../contracts/C6-api.md#automatic-execution-and-recovery)). Together with `price_per_unit`
(the quote's price) and `filled_at` (the execution time) they record how a fill was priced.

```powershell
Get-Content database/schema/013_execution_quote.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. Both columns are
nullable: orders filled before 013, including the seed data, have no stored quote source or time.

## 014 — Submission and validation audit

Apply `schema/014_submission_audit.sql` before starting any updated service: they all map
`audit_log` through `libs/common` with `ddl-auto=validate` and will not start without the new
columns. It prepares `audit_log` for the trail of an order submission ([C2](../contracts/C2-audit.md)):

| Change | Why |
|---|---|
| `order_id` and `account_id` are optional | A refused order saves no order row, and a caller refused access to an account has none of their own to record. |
| `request_id` added | The server-generated ID shared by every event of one submission; it identifies a refused order. A CHECK requires an order or a request ID on every row. |
| `event_key` added, unique | One value per submission, event type and rule, so the same event can never be stored twice. Null for events that aren't part of a submission. |
| `action` allows `RULE_CHECKED` | One event per validation check. 010 carries the same list, because it is re-applied before 014. |

```powershell
Get-Content database/schema/014_submission_audit.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. Existing rows are
unchanged: they keep their order and account, and have no request ID or event key.

## 015 — Seed data for the submission audit

`schema/015_seed_submission_audit.sql` brings the audit events seeded by 011 to the shape 014
introduced ([C3](../contracts/C3-seed-data.md)): seeded `SUBMITTED` and `VALIDATED` events get a
request ID and event key, every validated seed order gets a passed `RULE_CHECKED` per rule, and
`seed_notrading` gets one refused submission with no order. It is a separate file because 011 runs
before 014 adds those columns, and because 011 is skipped on a database that already has the seed.

```powershell
Get-Content database/schema/015_seed_submission_audit.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

Idempotent, and it only touches the `seed_*` clients' events, so it is safe on an existing
database; without the seed it does nothing. It needs 014 first. The added checks get later
`audit_id`s than their orders' other events, so read a seeded trail in `created_at` order.

## 016 — Audit lockdown

`schema/016_audit_lockdown.sql` makes audit records impossible to change or delete
([C2](../contracts/C2-audit.md#the-lockdown)). It covers `audit_log` and the unused `order_events`.

| Change | Why |
|---|---|
| New login `lemarket_app` | The restricted account for the services. It owns nothing and is no superuser, so it cannot grant itself more. |
| Grants for `lemarket_app` | Ordinary read and write on every table, now and for tables created later, except the audit tables: `SELECT` and `INSERT` only. |
| Triggers on the audit tables | Refuse `UPDATE`, `DELETE` and `TRUNCATE` for every role, the owner included (SQLSTATE `LM001`). |

There are now two accounts:

| Account | For | Default password |
|---|---|---|
| `lemarket` (owner) | Migrations, seeding, resets, the `psql` calls in Jenkins and the scripts | `changeme`, from `DB_PASSWORD` |
| `lemarket_app` | The services and the `postgres-test` integration tests | `changeme_app`, from `APP_DB_PASSWORD` |

Apply it as the owner, like every other file. It needs `psql`, because it reads the new role's
password from the `APP_DB_PASSWORD` environment variable of the `psql` process.

```sh
docker compose up -d db   # recreates the container so it has APP_DB_PASSWORD
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/016_audit_lockdown.sql
```

With a native Windows PostgreSQL install, `lemarket` is not a superuser and cannot create the role.
Run `.\scripts\windows\setup-db.ps1` first: it creates `lemarket_app` as `postgres` and leaves an
existing database alone. Then:

```powershell
Get-Content database/schema/016_audit_lockdown.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. Existing rows are
unchanged. Earlier files that update or delete audit rows (009, 010, 015) are still safe to
re-apply: their statements match no rows by then, and the triggers fire per row.

After 016, a migration can no longer update or delete audit rows. A test that has to remove its own
audit events uses `AuditTestCleanup` (see C2).

### The record of refused attempts

A refused change to an audit record is recorded in the server log, with the account and the
statement ([C2](../contracts/C2-audit.md#refused-attempts-are-recorded)). The Compose `db` service
is set up for it. A native Windows install logs the error and the statement out of the box, but
not who ran it; to add that:

```powershell
.\scripts\windows\setup-db.ps1 -AuditLogging
```

This changes `log_line_prefix` for the whole PostgreSQL install and turns on DDL logging for the
`lemarket` database. The log files are in the `log` folder of the PostgreSQL data directory.

## 018 — Tables of the Kafka consumers

`schema/018_event_consumers.sql` adds one table for each consumer of the order events on Kafka
([C6](../contracts/C6-api.md#the-consumers-notifications-surveillance-and-market-activity)), each
then its own service. Apply it before starting trading- or staff-ops-service, which hold the
consumers now: both run with `ddl-auto=validate` and will not start without their tables.

| Table | Written by | One row per | The same event twice is caught by |
|---|---|---|---|
| `notifications` | trading-service (notifications) | status change of an order, for its client | `UNIQUE (order_id, status)` |
| `order_alerts` | staff-ops-service (surveillance) | order large enough to alert Trading Ops | `UNIQUE (order_id)`, which [019](#019--one-alert-per-order-and-reason) replaces with `UNIQUE (order_id, reason)` |
| `trade_activity` | trading-service (market activity) | fill; it has no account column | the primary key, `order_id` |

```powershell
Get-Content database/schema/018_event_consumers.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. `lemarket_app` needs no
grant: 016 gives it read and write on every table created later.

None of the three tables has a foreign key to `orders`, `accounts` or `instruments`, on purpose.
Their rows are written from events, after the order's own transaction, and must never stop an
order, an account or a test fixture from being deleted. A row can therefore outlive its order.
The rows are derived data: emptying a table loses nothing an order or the audit trail needs.

## 019 — One alert per order and reason

`schema/019_order_alert_reasons.sql` lets an order have more than one alert, one per reason
([C6](../contracts/C6-api.md#get-apiv1surveillancealerts-trading_ops-only)). Apply it before
starting the updated staff-ops-service (it runs with `ddl-auto=validate` and will not start
without the new columns), and with the old one stopped: the old one does not fill `occurred_at`,
so its inserts would be refused.

| Change | Why |
|---|---|
| `UNIQUE (order_id)` becomes `UNIQUE (order_id, reason)` | A large order can also be alerted to for another reason. The key is still what catches the same event delivered twice. |
| `instrument_id`, `side`, `quantity` and `submitted_at` are optional | They describe the order as it was placed, which only the `OrderSubmitted` event carries. An alert raised from another event has the order, its account and why. |
| `rejection_reason` added | Why the order was rejected, for an alert about a rejection. A `RejectionReason` code as text, with no CHECK, like the statuses in `notifications`. |
| `occurred_at` added, required | When what the alert is about happened. Alerts are listed newest first by it; `order_alerts_occurred_idx` replaces `order_alerts_submitted_idx`. |

```powershell
Get-Content database/schema/019_order_alert_reasons.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. Existing alerts are
kept: each is about a placement, so its `occurred_at` is set to its `submitted_at`.

## 020 — Segment report demo clients

`schema/020_segment_report_demo_clients.sql` adds dedicated fictional clients and funded
accounts for the LMKT-43 proof of concept. It derives segments from 011's seed clients
and is safe to reapply without resetting account balances. Apply it as the database owner:

```powershell
psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket -f database/schema/020_segment_report_demo_clients.sql
python scripts/windows/seed-segment-report.py
```

The second command requires the running trading stack and an open market. It places trades
through the API, keeping cash, holdings, settlement receipts and audit events consistent.
See [C3](../contracts/C3-seed-data.md#segment-report-proof-of-concept) for the demo personas
and rerun behavior. No existing history is deleted or backdated.

## 021 - Historical segment report demo clients

`021_historical_segment_demo_clients.sql` adds the fictional historical-report personas
without resetting existing accounts. Apply it to the existing local database:

```powershell
psql -h localhost -U lemarket -d lemarket -v ON_ERROR_STOP=1 -f database/schema/021_historical_segment_demo_clients.sql
.\scripts\windows\seed-historical-segment-report.ps1 -Apply
```

The PowerShell companion requires Maven, a JDK and the migrated local PostgreSQL database.
It reads `DB_PASSWORD`, with the same local development fallback as setup-db.ps1;
`DEMO_DATABASE_URL` (JDBC URL) and `DEMO_DATABASE_USER` can select another demo database.
Omit `-Apply` to verify the history in a transaction that rolls back. The numbered SQL
creates only clients; trade history requires this explicit companion command and is not
added to CI automatically. See [C3](../contracts/C3-seed-data.md#historical-client-segment-demo-2026)
for the historical fixture's scope, reporting dates and idempotency rules.

## 022 — Daily price history

`022_price_history.sql` adds `price_history`: one OHLC bar per stock per exchange-local trading day,
keyed by `(instrument_id, trading_date)`. It backs the 1W–6M ranges of the stock and portfolio
charts ([C6](../contracts/C6-api.md#chart-ranges)); 1D still reads `price_candles`.

| Part | What it does |
|---|---|
| Table | New. `lemarket_app` gets read and write rights through the default privileges of [016](#016--audit-lockdown). |
| Seed | Six months of weekdays before the day it runs, for every stock with market parameters. Each line is a random walk with the stock's own drift and volatility, its random numbers hashed from (stock, date) so the same day always seeds the same history. It is bent to pass through the price of every FILLED order in the window and to end at the stock's stored quote (else its initial price), so trades sit on the chart and it joins the live price. Today is left to trading-service's simulator. Skipped once the table has rows. |
| Upkeep | trading-service's simulator writes each stock's bar for the current trading day every time it saves prices, so the history grows by a day per trading day. |

Apply it to an existing local database:

```powershell
psql -h localhost -U lemarket -d lemarket -v ON_ERROR_STOP=1 -f database/schema/022_price_history.sql
```

A database whose stored quotes have drifted far from its seeded fills (a long-running local one)
gets a steep line between the last fill and today; a fresh database does not.

## 023 — Overnight report runs

`schema/023_report_runs.sql` adds `report_runs`: one row per overnight report per night, with its
status, when it was ready and why it failed (LMKT-45,
[C6](../contracts/C6-api.md#get-apiv1reportsovernight)). Apply it before starting
staff-ops-service: it runs with `ddl-auto=validate` and will not start without the table.

```powershell
Get-Content database/schema/023_report_runs.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. `lemarket_app` needs no
grant: 016 gives it read and write on every table created later.

It is the only table the reports in staff-ops-service write. Their connections stay read-only by
default, and only their run writer opens a read-write transaction for this table
([README](../services/staff-ops-service/README.md#overnight-reports-lmkt-45)).
`UNIQUE (report_name, run_date)` is what stops a report running twice in one night, however often
the scheduler is triggered or the service restarted. The table has no foreign keys and no client
data.

## 024 — Cash movement receipts

`schema/024_cash_movements.sql` adds `cash_movement_receipts`, the idempotency record for deposits
and withdrawals ([C6](../contracts/C6-api.md#deposit-and-withdraw-cash)). A request
ID is stored once with its account, movement, amount and resulting cash balance, so retrying it
cannot move cash twice. The migration also adds `CASH_DEPOSITED` and `CASH_WITHDRAWN` to the audit
action constraint for databases that already applied 010 and 014.

```powershell
Get-Content database/schema/024_cash_movements.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The receipt, cash balance change and audit event are committed in one trading-service transaction.

## 025 — Watchlist

`schema/025_watchlist.sql` adds `watchlist`: one row per account and stock a client follows
([C6](../contracts/C6-api.md#watchlist)). The unique constraint on `(account_id, instrument_id)`
refuses a duplicate even when two requests race, and trading-service answers it as
`409 ALREADY_WATCHED`. Prices are not stored; the trading app shows each stock's live quote.

```powershell
Get-Content database/schema/025_watchlist.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

Rows go with their account (`ON DELETE CASCADE`). No grant is needed: 016's default privileges
give `lemarket_app` the new table.

## 026 — Audit the waiting steps

`schema/026_audit_waiting_steps.sql` adds `PENDING` and `DELAYED` to the audit action constraint.
orders-service now audits every status an order moves to, so an order's trail shows when it waited
for the market to open and when it started executing ([C2](../contracts/C2-audit.md#the-execution-trail)).
010, 014 and 024 carry the same list. A clean database applies all of them after 011 has given the
seeded open orders their waiting step, so each must allow it.

```powershell
Get-Content database/schema/026_audit_waiting_steps.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

It only redefines a constraint; no rows are written or changed.

## 027 — Account status history

`schema/027_account_status_changes.sql` adds `account_status_changes`: one row for every change to
`clients.account_status` after registration, written in the same transaction as the change (BR-03).
A row holds the old and new status, the reason, who changed it (a Trading Ops username, or the
client's own when they close their account) and when ([C6](../contracts/C6-api.md#account-status-trading_ops-only),
[C7](../contracts/C7-roles.md#who-may-use-what)). No expiry date is stored: Trading Ops expire an
account, nothing does it automatically.

```powershell
Get-Content database/schema/027_account_status_changes.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

History is insert-only for the services: the migration takes back the `UPDATE` and `DELETE` that
016's default privileges gave `lemarket_app`, leaving `SELECT` and `INSERT`, as on the audit tables.
auth-service validates the table at startup, so apply it before starting a new auth-service.

## 028 — Per-instrument location restrictions

`schema/028_instrument_location_restrictions.sql` adds `instrument_location_restrictions`: one row
per stock and residence location whose clients may not trade it (BR-05). `location` is a US state
name as the sign-up form stores it in `addresses.state` (`New Jersey`), or an ISO country code; it
is matched against a client's residential address, ignoring case. The platform-wide list
(`lmj.orders.restricted-locations`, [C5](../contracts/C5-config.md#who-uses-each-setting)) still
blocks every stock; this table narrows a rule to the stocks it concerns. orders-service checks both
at placement and at execution ([C6](../contracts/C6-api.md#post-apiv1orders)).

The migration blocks the six non-US companies' stocks for New Jersey and adds the persona
`seed_restricted`, who lives there ([C3](../contracts/C3-seed-data.md#clients)). No other seeded
client lives in New Jersey.

```powershell
Get-Content database/schema/028_instrument_location_restrictions.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

Rules are configuration kept in migrations: `lemarket_app` may only read the table. orders-service
validates it at startup, so apply it before starting a new orders-service.

## 029 — Trade record lockdown

`schema/029_trade_record_lockdown.sql` locks the trade records the way 016 locks the audit trail
(BR-14, [C2](../contracts/C2-audit.md#the-lockdown)):

| Table | `lemarket_app` may | Triggers refuse, for every role |
|---|---|---|
| `orders` | `SELECT`, `INSERT`, `UPDATE` (the lifecycle) | `UPDATE` of a row already `FILLED` or `REJECTED`; any `DELETE` or `TRUNCATE` |
| `settlement_receipts`, `cash_movement_receipts`, `account_status_changes` | `SELECT`, `INSERT` | any `UPDATE`, `DELETE` or `TRUNCATE` |

The application account gets `permission denied` (`42501`) for what it lacks, and everyone else,
the owner included, gets `Trade records are immutable` (`LM001`) from the triggers, which are
`ENABLE ALWAYS` like 016's. An account that ever moved cash can't be deleted either: 024's
`ON DELETE CASCADE` would take its receipts with it, and their trigger refuses that.

```powershell
Get-Content database/schema/029_trade_record_lockdown.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

Tests that insert their own orders remove them as the owner, switching `orders_no_delete` off
inside the deleting transaction only: `AuditTestCleanup` in the Java tests, `removeTestClients`
in the e2e suite (which needs `E2E_OWNER_DATABASE_URL`). Nothing in the application can.

## 031 — The amount an order was placed for

`schema/031_order_requested_amount.sql` adds `orders.requested_amount NUMERIC(15,2)`, null unless the
client placed the order by dollar amount (BR-04). orders-service turns an amount into as many whole
shares as it covers at the quote it priced the order from and saves those in `quantity`, as for any
order; `requested_amount` keeps what the client asked for ([C1](../contracts/C1-orders.md),
[C6](../contracts/C6-api.md#post-apiv1orders)). A CHECK keeps it positive.

```powershell
Get-Content database/schema/031_order_requested_amount.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

orders-service validates the column at startup, so apply it before starting a new orders-service.
030 is reserved for later work and may be added after it; the files still apply in number order.

## Backups and restore

BR-14. Backups are `pg_dump` custom-format files (compressed; `pg_restore` restores all of a file or
one table), named `lemarket-<UTC time>.dump`, taken as the owner `lemarket`. Each run keeps the
newest 7 (`BACKUP_KEEP`). A dump is written under a temporary name and renamed when complete, so a
failed run never counts as a backup and never prunes a good one.

| How | Where the files go |
|---|---|
| Docker: the `db-backup` service runs [scripts/backup-db.sh](../scripts/backup-db.sh) every night at 02:00 UTC (`BACKUP_TIME`), and once at first start | the `db_backups` volume; `docker compose logs db-backup` lists each run |
| By hand, Mac or Linux: `sh scripts/backup-db.sh` from the repo root (uses the Compose db container, so no PostgreSQL client is needed; with `PGHOST` set it runs a local `pg_dump` instead) | `backups/` at the repo root (git-ignored) |
| By hand, native Windows: `.\scripts\windows\backup-db.ps1` (schedule it with Task Scheduler for nightly backups) | `backups\` at the repo root |

Copy a backup out of the Docker volume (`docker compose down -v` deletes the volume with the database):

```sh
docker compose cp db-backup:/backups ./backups-from-docker
```

**Check a backup** by restoring it into a scratch database next to the live one, then compare:

```sh
docker compose exec -T db createdb -U lemarket lemarket_restore_test
docker compose exec -T db pg_restore -U lemarket --exit-on-error -d lemarket_restore_test < backups/lemarket-<time>.dump
docker compose exec -T db psql -U lemarket -d lemarket_restore_test -c "SELECT count(*), sum(cash_balance) FROM accounts"
docker compose exec -T db dropdb -U lemarket lemarket_restore_test
```

**Restore over the live database** (this replaces all data since the backup):

```sh
docker compose stop auth-service orders-service trading-service staff-ops-service db-backup
docker compose exec -T db dropdb -U lemarket lemarket
docker compose exec -T db createdb -U lemarket lemarket
docker compose exec -T db pg_restore -U lemarket --exit-on-error -d lemarket < backups/lemarket-<time>.dump
docker compose start auth-service orders-service trading-service staff-ops-service db-backup
```

Native Windows: the same with `dropdb`, `createdb` and `pg_restore` from the PostgreSQL `bin`
folder, adding `-h localhost -U lemarket`. Restore into a new database rather than with
`pg_restore --clean`: the audit and trade-record triggers refuse the `TRUNCATE`/`DELETE` a clean
would need, which is the point of them. A restore keeps the triggers armed and the grants of
`lemarket_app`. On a new PostgreSQL install, create the `lemarket_app` role first (as `setup-db.ps1`
or 016 does): roles live outside the database, so a dump does not carry them.
