# C4 – Simulated quote feed

Pricing, failure handling and the indicative price can be tested without a live provider. market-service simulates every stock's price; tests can set prices and break the feed on demand.

## Where it's defined

| What | Code |
|---|---|
| How prices move (GBM) | [MarketSimulator.java](../services/market-service/src/main/java/com/lemarketjames/market/service/MarketSimulator.java), per-stock parameters in `instrument_market_params` ([006](../database/schema/006_market_simulation.sql), [009](../database/schema/009_full_lebron_market.sql)) |
| Simulator settings: tick rate, seed, speed, market hours, holidays, controls | [MarketSimulationProperties.java](../services/market-service/src/main/java/com/lemarketjames/market/config/MarketSimulationProperties.java) |
| Trading hours per exchange | [MarketHours.java](../libs/market-client/src/main/java/com/lemarketjames/market/model/MarketHours.java) |
| Feed modes, and what each one means | [FeedMode.java](../services/market-service/src/main/java/com/lemarketjames/market/service/FeedMode.java) |
| The controls | [MarketFeedControl.java](../services/market-service/src/main/java/com/lemarketjames/market/service/MarketFeedControl.java), exposed by [MarketControlController.java](../services/market-service/src/main/java/com/lemarketjames/market/MarketControlController.java) |
| Is a quote too old? | [QuoteFreshness.java](../libs/market-client/src/main/java/com/lemarketjames/market/model/QuoteFreshness.java), with the limit from [C5](C5-config.md) |

## Controlling the feed

The controls exist only where `sim.control.enabled` is on. It is on in the Docker stack (`SIM_CONTROL_ENABLED` in `docker-compose.yml`) and in `scripts/windows/start-all.ps1`, and off otherwise. They are served by market-service itself on port 8083, under `/internal`, which the gateway never routes.

```bash
curl localhost:8083/internal/market/control                    # current mode and pinned stocks
curl -X PUT localhost:8083/internal/market/control/prices/AAPL \
     -H 'Content-Type: application/json' -d '{"price": 150}'   # set AAPL to 150 and hold it there
curl -X PUT localhost:8083/internal/market/control/prices/AAPL \
     -H 'Content-Type: application/json' -d '{"price": 150, "pinned": false}'  # set it, then let it move
curl -X DELETE localhost:8083/internal/market/control/prices/AAPL   # let a held price move again
curl -X PUT localhost:8083/internal/market/control/feed \
     -H 'Content-Type: application/json' -d '{"mode": "STALE"}' # or UNAVAILABLE, or LIVE
curl -X POST localhost:8083/internal/market/control/reset       # LIVE, nothing held
```

An unknown ticker or a non-positive price gets `400 { message }`.

The feed mode applies to the whole stack, and so does a held price. End-to-end tests that change the mode therefore run in the `feed-failure` Playwright project, after every other test ([playwright.config.ts](../apps/e2e/playwright.config.ts)); a test that holds a price picks a stock no other test holds.

## What consumers see

- **UNAVAILABLE:** every market-service quote endpoint answers `503`. `MarketDataClient` treats that as "no quote", so a BUY is refused with `PRICE_UNAVAILABLE`, and `GET /api/quotes` returns an empty list.
- **STALE:** quotes stop changing, and their `lastUpdated` is pushed into the past, so `QuoteFreshness.isStale` is true straight away. Buy-sell refuses stale BUY placement and rejects execution with `STALE_QUOTE`. The stored prices are never overwritten with the backdated copies.
- **A set price** shows up in every quote at once. Day high and low stretch to include it. A held price is still a live quote: while the stock's exchange is open its `lastUpdated` keeps advancing each tick, so orders on a held stock are not refused as stale. Use STALE to test staleness.

## Market hours

With `sim.respect-market-hours` on (the default), a stock's price only moves while its exchange is open: weekdays in the `MarketHours` session, excluding `sim.holidays`. Turn it off (`SIM_RESPECT_MARKET_HOURS=false`) to see prices move outside hours in development.
