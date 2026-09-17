# Market Simulation

How LeMarketJames produces prices. Covers the model, where state lives, how to configure and tune
it, and how other features should read prices.

Related: [API-CONTRACTS.md](../API-CONTRACTS.md) · [database/README.md](../database/README.md) ·
[AGENTS.md](../AGENTS.md)

## Why it exists

The platform has no real market data feed, but the business requirements need one:

| Requirement | What it demands |
|---|---|
| BR-08 | An order is priced against a **current, non-stale** quote at execution |
| BR-12 | Current quotes across equities (US/UK/India), FX and crypto |
| BR-13 | An **indicative price** before an order is submitted |
| 9.1 | A client's displayed value stays correct across restarts and partial failures |

So the backend simulates a market: prices that move on their own, continuously, and survive a
restart. Only US equities are seeded today; the model already handles other asset classes.

## The model: Geometric Brownian Motion

GBM is the standard model for stock prices: returns are random, proportional to price, and
log-normally distributed, so a price can never go negative. Each tick applies the **exact**
discrete solution (not an Euler approximation):

```
S(t + Δt) = S(t) · exp( (μ − σ²/2) · Δt + σ · √Δt · Z ),    Z ~ N(0, 1)
```

- **μ (drift)** — annualised expected return, e.g. `0.08` = +8%/year
- **σ (volatility)** — annualised volatility, e.g. `0.25` = 25%/year
- **Δt** — the tick length in *trading* years: `elapsed seconds / (trading days × session seconds)`.
  For US equities a trading year is `252 × 6.5 × 3600` seconds.
- **Z** — a standard normal random draw

The `−σ²/2` term (the Itô correction) is what keeps the *expected* price growing at exactly μ;
without it prices drift upward faster than configured. `GbmModelTest` asserts the mean and
variance of the log returns statistically.

### Stocks move together

Independent random walks look artificially uncorrelated. Each tick draws **one market-wide shock**
plus one shock per instrument and blends them (a one-factor model):

```
Z_i = ρ_i · Z_market + √(1 − ρ_i²) · Z_own
```

`Z_i` is still standard normal, so volatility is unchanged, but instruments with a higher
`market_correlation` (ρ) move with the market more often, as real stocks do on market-wide news.

### Derived values

Only the price is simulated; everything else is derived:

| Value | How |
|---|---|
| Bid / ask | mid price ∓ `spread_bps / 2` |
| Day open / high / low | tracked per trading day, reset at the first tick of a new day |
| Previous close | the last price of the previous trading day |
| Change, change % | versus previous close |
| Volume | `avg_daily_volume` spread across the session × lognormal noise × a factor that rises with the size of the price move |
| Market cap | price × `shares_outstanding` |
| P/E ratio | price ÷ `earnings_per_share` |
| Dividend yield | `dividend_per_share` ÷ price × 100 |

### Trading hours

`MarketHours` decides when prices may move, and how long a trading year is. Exchange holidays and
half days are **not** modelled.

| Market | Session | Trading days/year |
|---|---|---|
| US equities (default) | 09:30–16:00 America/New_York, weekdays | 252 |
| UK equities | 08:00–16:30 Europe/London, weekdays | 252 |
| India equities | 09:15–15:30 Asia/Kolkata, weekdays | 250 |
| FX | 24h on weekdays (approximation) | 260 |
| Crypto | continuous | 365 |

While a market is closed, prices hold their last value. Set `sim.respect-market-hours=false` to
move prices around the clock during development.

## Where state lives

| State | Where | Why |
|---|---|---|
| Live price (changes each tick) | Backend memory (`MarketSimulator`) | Once per second per instrument is far too often to write to Postgres |
| Latest price per instrument | `market_quotes` (one row each), written every `sim.snapshot-interval-ms` | Lets the market resume after a restart and gives SQL/reporting a current price |
| Price history | `price_candles`, one 1-minute OHLC row per instrument | Charts and period performance; ticks would be too granular to keep |
| Per-instrument settings | `instrument_market_params` | Tunable without a redeploy |
| What can be traded | `instruments` | Owned by the orders feature; the market only reads it |

A holding's value is **never stored**: it is computed as `quantity × live price` when requested, so
it cannot go stale.

## Code layout

```
market/
├── model/        Immutable types + maths, no Spring: GbmModel, MarketHours,
│                 MarketInstrument, QuoteSnapshot, PriceCandle
├── entity/       JPA: InstrumentMarketParamsEntity, MarketQuoteEntity, PriceCandleEntity
├── repository/   Spring Data repositories
├── service/      MarketDataService (read API), MarketSimulator (the market),
│                 MarketScheduler (the clock), MarketPersistenceService, MarketInitializer
└── config/       sim.* properties, UTC Clock bean, scheduling switch
```

**Startup:** `MarketInitializer` loads every instrument that has parameters, plus its last stored
price, into the simulator. An instrument without parameters is not simulated and has no quote.

**Each tick** (`MarketScheduler.tick` → `MarketSimulator.tick`): draw the market shock, then for
every open instrument apply one GBM step, update day stats and volume, publish a new immutable
`QuoteSnapshot`, and close off a candle when the minute changes.

**Each save** (`MarketScheduler.saveSnapshot`): upsert the latest price of every instrument into
`market_quotes` and append completed candles. A failed save is logged and never stops the market.

### Threading

`tick` and `load` are synchronized and are the only writers of price state. Readers take immutable
snapshots from concurrent maps and never block, so a quote is never half-updated. Spring's default
scheduler is single-threaded, so a tick and a save cannot overlap.

**One simulator only.** Two backend instances would each run their own market and disagree on
prices. Compose runs one; scaling out would mean electing a single writer.

### Stall protection

A tick covers the real time since the previous tick, capped at 5 ticks' worth. Without the cap, a
stalled scheduler (long GC pause, a suspended VM) would resume with one enormous price jump.

### Determinism

Set `sim.seed` and the same market replays exactly — useful for demos, tests and bug reports.
Tests inject a fixed `Clock` and call `tick(Instant)` directly instead of waiting on the scheduler.

## Reading prices from other features

Depend on the interface, never on the simulator:

```java
Optional<QuoteSnapshot> quote = marketData.findByInstrumentId(instrumentId);
Optional<QuoteSnapshot> byTicker = marketData.findByTicker("AAPL");   // case-insensitive
Collection<QuoteSnapshot> all = marketData.findAll();
```

`QuoteSnapshot` carries the price, bid/ask, day stats, volume and a `lastUpdated` timestamp, and
derives change, market cap, P/E and dividend yield. Non-tradable instruments still have quotes: a
client may hold or watch something they cannot currently trade.

Current consumers: `QuoteService` (`GET /api/quotes/{symbol}`) and `HoldingsService` (current price
and value). Reading a quote never changes a price, so the number of callers does not affect the
market.

## Configuration

Runtime settings (`sim.*`, overridable per environment variable) are listed in the
[README](../README.md#market-simulation-settings). Per-instrument settings live in
`instrument_market_params` — see
[database/README.md](../database/README.md#tuning-the-market) for how to tune them and add
instruments.

Parameter values were chosen to be plausible for each stock (higher σ for TSLA and NVDA than for
MSFT) with starting prices matching the seeded fills in `001_core_schema.sql`, so seeded holdings
do not show an artificial gain or loss on first start.

## Tests

| Test | Covers |
|---|---|
| `GbmModelTest` | Drift-only growth, positivity under extreme shocks, statistical mean/variance of log returns, correlation of the blended shock |
| `MarketHoursTest` | Session boundaries in exchange-local time, weekends, crypto, trading-year length, asset-class/location mapping |
| `MarketSimulatorTest` | Start/resume, price moves only on a tick, closed markets frozen, seed determinism, trading-day rollover, candle production, stall protection |
| `QuoteServiceTest` | Contract mapping, symbol normalisation, unknown/blank symbols, derived fundamentals, and that reading a quote does not move the price |
| `HoldingsServiceTest` | Holdings valued at the market price; unsimulated instruments degrade to zero |

Tests run against H2 with `sim.enabled=false` and seed data in `src/test/resources/data.sql`.
**H2 cannot catch a mismatch between the entities and the real Postgres schema** — run the backend
against Postgres (`ddl-auto=validate`) after changing an entity or a migration.

## Known limitations and next steps

- **Constant volatility.** Plain GBM has no volatility clustering; real markets alternate calm and
  turbulent periods. Regime switching, GARCH or Heston would address it.
- **No jumps.** Earnings and news shocks would need a jump term (e.g. Merton).
- **No order book or price impact.** A client's order does not move the price.
- **Orders are not filled from the market yet** (BR-08/BR-09): `OrderService` still stores a
  client-supplied `pricePerUnit`. Filling at `MarketDataService`'s price, and recording the quote
  used in `order_events.quote_used` (which already expects bid/ask), is the next step.
- **No history endpoint.** `price_candles` is being filled, but nothing serves it; charts and the
  performance/volatility figures in `GET /api/reports/instruments` need a new endpoint, which is an
  API contract change.
- **Bid/ask is not exposed over HTTP.** It is simulated and persisted, but adding it to the quote
  response is a contract change and needs team approval.
- **Exchange holidays are not modelled**, so the market trades on 25 December.
- **Only US equities are seeded.** UK/India equities, FX and crypto need rows in `instruments` and
  `instrument_market_params`; the model already supports their hours and conventions.
