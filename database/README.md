# database

Raw SQL, applied manually (no migration tool). `schema/` holds numbered, ordered SQL files (`001_core_schema.sql`, `002_...`) that together define the current database structure — apply them in order against the `lemarket` Postgres database. Add new numbered files here for future schema changes rather than editing old ones in place.

## One database, shared by every service

All backend microservices connect to this same `lemarket` database. This folder is the only place the schema is defined. Every service runs with `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate only checks the tables that service maps and never creates or changes them.

| Tables | Written by | Read by |
|---|---|---|
| `clients`, `addresses` | auth-service (registration, last login, lockout) | holdings-service (profile), core-service (client status) |
| `staff_users` | seed data only (no staff admin yet) | auth-service (staff login) |
| `accounts` | auth-service (created at registration), holdings-service (cash on settlement) | every servlet service |
| `instruments` | migrations only | buy-sell-service (orders), core-service (`GET /api/v1/instruments`), market-service |
| `orders` | buy-sell-service | buy-sell-service, holdings-service (through buy-sell's API) |
| `settlement_receipts` | holdings-service | holdings-service (idempotent execution outcomes) |
| `holdings` | holdings-service (settlement) | holdings-service |
| `audit_log` | buy-sell-service and holdings-service, only through `AuditRecorder` ([C2](../contracts/C2-audit.md)) | — (audit views are planned) |
| `market_quotes`, `price_candles` | market-service | market-service |
| `instrument_market_params` | migrations only | market-service |
| `reporting_trades` (view) | — | reports and insights ([C6](../contracts/C6-api.md#internal-events-and-the-execution-interface-core-service)) |

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

**core-service will not start until 006 is applied** (Hibernate schema validation fails on the
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

Restart core-service to pick up changes. `initial_price` only applies when an instrument has no
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

Apply to an existing Docker database (idempotent). Stop market-service first: it holds LMT in
memory and would try to write LMT's price back after the delete.

```sh
docker compose stop market-service
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/009_full_lebron_market.sql
docker compose start market-service
```

```powershell
docker compose stop market-service
Get-Content database/schema/009_full_lebron_market.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket
docker compose start market-service
```

To restart a carried-over stock from its new `initial_price` instead of its stored price, delete its
snapshot (with market-service stopped):

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
market-service afterwards so it picks up `CAVS`):

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/010_shared_contracts.sql
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/011_seed_test_data.sql
docker compose restart market-service
```

With a native Windows PostgreSQL install, `.\scripts\windows\setup-db.ps1 -Reset` rebuilds
everything including both.

## 012 — Durable order execution

Apply `schema/012_order_execution.sql` before starting the updated buy-sell and holdings services.
It adds the order's durable settlement flag and the settlement receipt table. Existing orders
start with no intent; open orders are picked up by the execution worker. Final orders are untouched.
Back up an existing database and stop the old order writer before switching to buy-sell.

```powershell
Get-Content database/schema/012_order_execution.sql | psql -v ON_ERROR_STOP=1 -h localhost -U lemarket -d lemarket
```

The migration is idempotent; new Docker databases apply it automatically. Receipts deliberately
have no order foreign key, preserving idempotency even after order cleanup. Never delete receipts
while their order IDs could be retried or reused. Cash updates and receipts commit together;
all settlements acquire the account lock first to serialize concurrent buys and sells.
