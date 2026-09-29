# C6 – API and event contracts

Every REST endpoint, the internal events, the execution interface and the reporting data source. Front-end and back-end halves of a story meet here. Each endpoint below is checked against its controller; **Planned** marks endpoints that are agreed but not built yet.

Value lists (order statuses, rejection codes, roles, audit event types) are not repeated here. Each links to the one place it is defined.

## Rules for every public endpoint

- **Entry point.** Browsers call only the gateway (`:8080` in a container, `:8089` on the host), under `/api/**`. Paths under `/internal/**` exist only service-to-service and are never routed by the gateway.
- **Authentication.** Log in with `POST /api/auth/login`; it sets an HTTP-only `jwt` cookie that every other endpoint reads. A missing or expired cookie gets `401`. The cookie carries the caller's roles ([C7](C7-roles.md)).
- **Ownership.** Clients only see their own data. A foreign or unknown account or order id gets the same `403 { "success": false, "error": "Access denied", "code": "ACCOUNT_ACCESS_DENIED" }`, so ids never leak.
- **Money** is a JSON number in USD. **Timestamps** in order and session DTOs are local date-times without an offset (`2026-09-21T10:30:00`). Quote times are ISO-8601 UTC.
- **Validation failures** return `400 { "errors": { "<field>": "<message>" } }`. Other bad input returns `400 { "message": "..." }`.

## Auth (auth-service)

| Method & path | Request | Success |
|---|---|---|
| `POST /api/auth/register` | `username, password, email, fullName, streetAddress, apartment?, city, state, zipCode, country, ssn, initialDeposit, investmentExperience ("beginner" / "experienced"), employmentStatus, dateOfBirth (yyyy-MM-dd), phoneNumber` | `201 { username, message }` |
| `POST /api/auth/login` | `{ "username": "<email>", "password": "..." }`: the email goes in `username` | `200 { username, roles, accountId?, message }` and the `jwt` cookie |
| `POST /api/auth/logout` | none | `200 { message }`; clears the cookie |
| `GET /api/auth/me` | none | `200 { username, roles, accountId? }` |

- `roles` is an array of [Role](../libs/common/src/main/java/com/lemarketjames/common/security/Role.java) names. `accountId` is present only for `CLIENT` logins; staff have no trading account.
- Login fails with `400 { message }` for a wrong email or password, a locked login ([C5](C5-config.md) lockout), or a login that isn't allowed in (e.g. a closed client, see [AccountStatus](../libs/common/src/main/java/com/lemarketjames/common/domain/AccountStatus.java)).
- Registration errors are `400 { errors }` per field, or `400 { message }` for a duplicate username or email.

## Instruments (core-service)

### GET /api/v1/instruments

The supported stock list: the `instruments` table, in id order, suspended stocks included. The frontend loads it once and keeps no copy of its own.

```json
[
  { "instrumentId": 1, "symbol": "AAPL", "name": "BronApple", "tradable": true },
  { "instrumentId": 52, "symbol": "CAVS", "name": "Cavaliers Media Group", "tradable": false }
]
```

Prices aren't included; they come from `GET /api/quotes`.

## Orders (core-service)

Order statuses and allowed moves: [contract C1](C1-orders.md). Rejection codes: [RejectionReason](../services/core-service/src/main/java/com/lemarketjames/orders/entity/RejectionReason.java).

### POST /api/v1/orders

Places a market order for one of the caller's accounts.

```json
{ "accountId": 7, "instrumentId": 5, "orderType": "BUY", "quantity": 1 }
```

- `orderType` is `BUY` or `SELL`. IDs and quantity must be positive; the UI sends whole shares.
- For a BUY, the server prices the order at the current ask from `MarketDataService`, rounded to 4 decimals, and uses that for both the cash check and the saved `pricePerUnit`. Any `pricePerUnit` the client sends is ignored.
- For a SELL, holdings-service checks the holding before anything is saved. No price is saved until the order fills.
- Placing an order saves it as `SUBMITTED` and writes its `SUBMITTED` and `VALIDATED` audit events ([C2](C2-audit.md)). It doesn't reserve cash, fill the order or change holdings.

**Response `201`** (the order DTO, also returned by every read below):

```json
{
  "success": true, "reason": null, "code": null,
  "orderId": 42, "accountId": 7, "instrumentId": 5, "orderType": "BUY", "quantity": 1,
  "pricePerUnit": 244.2366, "orderStatus": "SUBMITTED", "rejectionReason": null,
  "submittedAt": "2026-09-21T10:30:00", "acceptedAt": null, "filledAt": null,
  "createdAt": "2026-09-21T10:30:00", "updatedAt": "2026-09-21T10:30:00"
}
```

**Failures.** A refused order saves nothing.

| HTTP | When | Body |
|---|---|---|
| 400 | The client or account may not trade | `success: false, code: ACCOUNT_RESTRICTED, reason` |
| 400 | Suspended stock | `success: false, code: NOT_TRADABLE, error` |
| 400 | Not enough shares (SELL) | `success: false, code: INSUFFICIENT_HOLDINGS, error` |
| 400 | Not enough cash (BUY) | `success: false, code: INSUFFICIENT_CASH, reason` |
| 400 | No usable quote (BUY) | `success: false, code: PRICE_UNAVAILABLE, reason` |
| 400 | Invalid fields / unknown instrument | `errors` / `message` |
| 401 / 403 | Not logged in / not the caller's account | see rules above |

### POST /api/v1/buy-orders

The same as `POST /api/v1/orders` with `orderType` fixed to `BUY`. Body: `{ accountId, instrumentId, quantity, pricePerUnit }`. `pricePerUnit` is required, but it is validation input only; the server still uses the live ask. The trade dialog uses this endpoint for buys.

### Reading orders

| Method & path | Returns |
|---|---|
| `GET /api/v1/orders/{orderId}` | one order DTO |
| `GET /api/v1/orders/account/{accountId}` | the account's orders (`[]` if none) |
| `GET /api/v1/orders/account/{accountId}/status/{status}` | the account's orders in one status |
| `GET /api/v1/orders/instrument/{instrumentId}` | the caller's own orders for one stock |

### Changing an order's status (TRADING_OPS only)

| Method & path | Effect |
|---|---|
| `PUT /api/v1/orders/{orderId}/status/{status}` | Moves the order to `status` if its lifecycle allows it. Moving to `FILLED` needs a price and settles the order through holdings-service first. |
| `POST /api/v1/orders/{orderId}/reject?reason=CODE` | Rejects an open order. `reason` is a RejectionReason code; the default is `REJECTED_BY_OPERATIONS`. |

- Clients get `403` from both endpoints, even for their own orders.
- A move the lifecycle doesn't allow gets `409 { "success": false, "code": "INVALID_STATUS_TRANSITION", "error" }`.
- An unknown reason code gets `400`.

## Holdings, balance, profile, trades (holdings-service)

| Method & path | Returns |
|---|---|
| `GET /api/v1/holdings?accountId=…` (alias `/api/holdings`) | `{ success, holdings: [{ symbol, quantity, averageCost, currentPrice, totalCost, currentValue, gainLoss, gainLossPercent }] }` |
| `POST /api/v1/holdings/validate` | Body `{ accountId, instrumentId, sellQuantity }` → `200 { success: true, message }`, or `400 { success: false, error }` when there aren't enough shares |
| `GET /api/balance` (aliases `/api/v1/balance`, `/api/v1/portfolio`) | `{ success, balance: { cash, invested, totalValue, buyingPower, dayGainLoss, dayGainLossPercent, totalGainLoss, totalGainLossPercent, currency } }`. No accountId: scoped to the caller. `400 { message }` if the login has no account (e.g. staff). |
| `GET /api/v1/profile` | `{ username, fullName, email, phone, accountId, cashBalance, currency, tradingEnabled, openedDate }` for the caller |
| `GET /api/v1/trades?accountId=…` | the account's filled orders: `[{ symbol, side, quantity, pricePerUnit, filledAt }]` |

How the balance fields are computed:

| Field | Rule |
|---|---|
| `cash` | `accounts.cash_balance` |
| `buyingPower` | `cash` minus the cost of the account's open BUY orders (open = not final, see `OrderStatus.isOpen` in [Order.java](../services/core-service/src/main/java/com/lemarketjames/orders/entity/Order.java)) |
| `invested` | Σ holding quantity × current price |
| `totalValue` | `cash + invested` |
| `dayGainLoss(%)` | Σ quantity × (price − today's open); % relative to Σ quantity × open |
| `totalGainLoss(%)` | the holdings totals, relative to total cost |

## Quotes (core-service, reading market-service)

| Method & path | Returns |
|---|---|
| `GET /api/quotes/{symbol}` | `{ success: true, quote }`, or `404 { success: false, error: "Symbol not found" }` |
| `GET /api/quotes` | `{ success: true, quotes: [quote…] }` sorted by symbol. If market-service is unavailable, it returns `quotes: []` with `200`. |

`quote` = `{ symbol, name, price, priceChange, priceChangePercent, highPrice, lowPrice, openPrice, volume, marketCap, peRatio, dividendYield, lastUpdate }`.

## Sessions (core-service)

`POST /api/sessions/validate?accountId=…` with header `Authorization: Bearer <jwt>` returns `200 { success: true, session: { sessionId, accountId, expiresAt, isActive }, message }`, or `401 { success: false, message }` once the token has expired.

## Internal endpoints (service-to-service only, never through the gateway)

| Method & path | Caller → callee | Purpose |
|---|---|---|
| `GET /api/market/quotes`, `/quotes/{ticker}`, `/quotes/by-instrument/{id}` | core, holdings → market-service | Raw `QuoteSnapshot`s. Answers `503` while the feed is UNAVAILABLE ([C4](C4-quote-feed.md)). |
| `POST /internal/holdings/settle` | core → holdings-service | Applies a fill's cash and holdings changes, and audits `SETTLED` |
| `POST /internal/holdings/validate` | core → holdings-service | SELL holdings check at placement |
| `/internal/market/control/**` | tests → market-service | Quote-feed test controls, only when enabled; see [C4](C4-quote-feed.md) |

## Internal events and the execution interface (core-service)

- **Events.** [OrderStatusChanged](../services/core-service/src/main/java/com/lemarketjames/orders/events/OrderStatusChanged.java) is published after every status transition. [OrderFilled](../services/core-service/src/main/java/com/lemarketjames/orders/events/OrderFilled.java) is published in addition when an order fills. Both are in-process Spring events. Listen with `@EventListener`, or with `@TransactionalEventListener` to act only after the transition commits. They are not sent to other services.
- **Execution interface.** [OrderExecutor](../services/core-service/src/main/java/com/lemarketjames/orders/execution/OrderExecutor.java) takes an accepted order and returns an [ExecutionResult](../services/core-service/src/main/java/com/lemarketjames/orders/execution/ExecutionResult.java): fill at a price, reject with a reason, or wait. The executor never changes status itself. Its caller applies the result through the normal transitions, so audit, events and settlement always happen the same way.
- **Reporting data source.** The `reporting_trades` view ([010](../database/schema/010_shared_contracts.sql)) has one row per filled order, with the client's segment, symbol, side, quantity, price, gross amount and times. Reports and insights read from it rather than joining the order tables themselves.

## Planned (agreed, not built)

These are contracts for upcoming stories. Build them as written, or update this section in the same PR.

- **Audit (COMPLIANCE):** `GET /api/v1/audit?orderId=…|clientId=…&from=&to=` → `{ success, events: [{ eventType, orderId, accountId, clientId, occurredAt, details }] }`, oldest first, online events only ([C2](C2-audit.md), [C5](C5-config.md) retention).
- **Reports (ANALYST, COMPLIANCE):** built on `reporting_trades`.
  - `GET /api/reports/activity?startDate=&endDate=&limit=50&offset=0` → `{ success, activities: [{ id, type, symbol, quantity, price, totalAmount, fee, timestamp }], total, limit, offset }`.
  - `GET /api/reports/instruments?sortBy=gainLoss|gainLossPercent|quantity|value&order=ASC|DESC` → `{ success, instruments: [{ symbol, quantity, totalValue, gainLoss, gainLossPercent, performance: { week, month, threeMonth, year }, volatility, beta }] }`.
- **Order symbol fields:** add `symbol` and `instrumentName` to the order DTO, taken from `instruments`. This is non-breaking and lets `orders-panel.ts` stop resolving ids itself.
- **Candles:** `GET /api/v1/instruments/{symbol}/candles?limit=60` (max 390) → `{ success, symbol, interval: "1m", candles: [{ time, open, high, low, close, volume }] }` from `price_candles`, oldest first. An unknown symbol gets `404` in the quotes shape.
- **Watchlist:** `GET /api/v1/watchlist`, `POST /api/v1/watchlist { symbol }` (`409 ALREADY_WATCHED`), `DELETE /api/v1/watchlist/{symbol}`. It needs a new `watchlist (account_id, instrument_id, added_at)` table in the next free migration number, in its own `watchlist` feature package.

## Changing a contract

- Endpoint paths and methods, request fields and response fields are fixed once published. Additive, backward-compatible changes are fine.
- Removing or renaming anything needs team agreement first.
- Change this file in the same PR as the code, so it never describes something the code doesn't do.
