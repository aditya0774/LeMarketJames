# database

Raw SQL, applied manually (no migration tool). `schema/` holds numbered, ordered SQL files (`001_core_schema.sql`, `002_...`) that together define the current database structure — apply them in order against the `lemarket` Postgres database. Add new numbered files here for future schema changes rather than editing old ones in place.

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
(`com.lemarketjames.market`, design notes in [docs/MARKET.md](../docs/MARKET.md)):

| Table | Change | Purpose |
|---|---|---|
| `instruments` | Adds AMZN, TSLA, NVDA (ids 4–6) | Every quoted symbol is also a tradable instrument. Ids 1–3 are unchanged. |
| `instrument_market_params` | New, one row per instrument | How each price moves: initial price, drift, volatility, market correlation, bid/ask spread, shares outstanding, average daily volume, EPS, dividend. |
| `market_quotes` | Now one row per instrument; adds last/open/high/low/previous close/volume | Latest price snapshot, refreshed every few seconds so prices survive a restart. |
| `price_candles` | New | 1-minute OHLC price history. |

**The backend will not start until 006 is applied** (Hibernate schema validation fails on the
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
scripts, **deleting any local data**.

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

Restart the backend to pick up changes. `initial_price` only applies when an instrument has no
row in `market_quotes`; delete that row to restart an instrument from its initial price.

To add an instrument to the market, insert it into `instruments` and add a matching
`instrument_market_params` row in a new numbered migration. Instruments without params are not
quoted.
