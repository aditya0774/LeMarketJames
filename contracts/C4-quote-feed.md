# C4 – Simulated quote feed

Pricing, failure handling and the indicative price can be tested without a live provider. trading-service's market simulator prices every stock; tests can set prices and break the feed on demand.

## Where it's defined

| What | Code |
|---|---|
| How prices move (GBM) | [MarketSimulator.java](../services/trading-service/src/main/java/com/lemarketjames/market/service/MarketSimulator.java), per-stock parameters in `instrument_market_params` ([006](../database/schema/006_market_simulation.sql), [009](../database/schema/009_full_lebron_market.sql)) |
| Simulator settings: tick rate, seed, speed, market hours, holidays, controls | [MarketSimulationProperties.java](../services/trading-service/src/main/java/com/lemarketjames/market/config/MarketSimulationProperties.java) |
| Trading hours per exchange | [MarketHours.java](../libs/market-client/src/main/java/com/lemarketjames/market/model/MarketHours.java) |
| Feed modes, and what each one means | [FeedMode.java](../services/trading-service/src/main/java/com/lemarketjames/market/service/FeedMode.java) |
| The controls | [MarketFeedControl.java](../services/trading-service/src/main/java/com/lemarketjames/market/service/MarketFeedControl.java), exposed by [MarketControlController.java](../services/trading-service/src/main/java/com/lemarketjames/market/MarketControlController.java) |
| Is a quote too old? | [QuoteFreshness.java](../libs/market-client/src/main/java/com/lemarketjames/market/model/QuoteFreshness.java), with the limit from [C5](C5-config.md) |
| Is the feed currently down, from a consumer's point of view? | [MarketFeedStatus.java](../libs/market-client/src/main/java/com/lemarketjames/market/client/MarketFeedStatus.java), updated by every [MarketDataClient.java](../libs/market-client/src/main/java/com/lemarketjames/market/client/MarketDataClient.java) call |

## Controlling the feed

The controls exist only where `sim.control.enabled` is on. It is on in the Docker stack (`SIM_CONTROL_ENABLED` in `docker-compose.yml`) and in `scripts/windows/start-all.ps1`, and off otherwise. They are served by trading-service itself on port 8084, under `/internal`, which neither web app routes.

```bash
curl localhost:8084/internal/market/control                    # current mode and pinned stocks
curl -X PUT localhost:8084/internal/market/control/prices/AAPL \
     -H 'Content-Type: application/json' -d '{"price": 150}'   # set AAPL to 150 and hold it there
curl -X PUT localhost:8084/internal/market/control/prices/AAPL \
     -H 'Content-Type: application/json' -d '{"price": 150, "pinned": false}'  # set it, then let it move
curl -X DELETE localhost:8084/internal/market/control/prices/AAPL   # let a held price move again
curl -X PUT localhost:8084/internal/market/control/feed \
     -H 'Content-Type: application/json' -d '{"mode": "STALE"}' # or UNAVAILABLE, or LIVE
curl -X POST localhost:8084/internal/market/control/reset       # LIVE, nothing held
```

An unknown ticker or a non-positive price gets `400 { message }`.

The feed mode applies to the whole stack, and so does a held price. End-to-end tests that change the mode therefore run in the `feed-failure` Playwright project, after every other test ([playwright.config.ts](../apps/e2e/playwright.config.ts)); a test that holds a price picks a stock no other test holds.

## What consumers see

- **UNAVAILABLE:** every `/api/market/**` quote and history endpoint answers `503`. orders-service's `MarketDataClient` treats that as "no quote", so a BUY is refused with `PRICE_UNAVAILABLE`. trading-service's own features read the simulator in-process through [InProcessMarketData](../services/trading-service/src/main/java/com/lemarketjames/market/inprocess/InProcessMarketData.java) and [InProcessPriceHistory](../services/trading-service/src/main/java/com/lemarketjames/market/inprocess/InProcessPriceHistory.java), which answer the same way: no quote and no history, so `GET /api/quotes` returns an empty list and charts are empty.
- **STALE:** quotes stop changing, and their `lastUpdated` is pushed into the past, so `QuoteFreshness.isStale` is true straight away. orders-service refuses stale BUY placement and rejects execution with `STALE_QUOTE`. The stored prices are never overwritten with the backdated copies.
- **A set price** shows up in every quote at once. Day high and low stretch to include it. A held price is still a live quote: while the stock's exchange is open its `lastUpdated` keeps advancing each tick, so orders on a held stock are not refused as stale. Use STALE to test staleness.

## Market hours

With `sim.respect-market-hours` on (the default), a stock's price only moves while its exchange is open: weekdays in the `MarketHours` session, excluding `sim.holidays`. Turn it off (`SIM_RESPECT_MARKET_HOURS=false`) to see prices move outside hours in development.
