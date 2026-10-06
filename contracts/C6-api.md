# C6 – API and event contracts

Every REST endpoint, the internal events, the execution interface and the reporting data source. Front-end and back-end halves of a story meet here. Each endpoint below is checked against its controller; **Planned** marks endpoints that are agreed but not built yet.

Value lists (order statuses, rejection codes, roles, audit event types) are not repeated here. Each links to the one place it is defined.

## Rules for every public endpoint

- **Entry point.** Browsers call only the gateway (`:8080` in a container, `:8089` on the host), under `/api/**`. Paths under `/internal/**` exist only service-to-service and are never routed by the gateway.
- **Authentication.** Log in with `POST /api/auth/login`; it sets an HTTP-only `jwt` cookie that every other endpoint reads. A missing or expired cookie gets `401`. The cookie carries the caller's roles ([C7](C7-roles.md)).
- **Ownership.** Clients only see their own data. A foreign or unknown account or order id gets the same `403 { "success": false, "error": "Access denied", "code": "ACCOUNT_ACCESS_DENIED" }`, so ids never leak.
- **Money** is a JSON number in USD. **Order timestamps** represent UTC and currently serialize without an offset (`2026-09-21T10:30:00`); clients must interpret them as UTC. Session DTOs retain their existing local date-time format. Quote times are ISO-8601 UTC.
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

## Orders (buy-sell-service)

Order statuses and allowed moves: [contract C1](C1-orders.md). Rejection codes: [RejectionReason](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/entity/RejectionReason.java).

### POST /api/v1/orders

Places a market order for one of the caller's accounts.

```json
{ "accountId": 7, "instrumentId": 5, "orderType": "BUY", "quantity": 1 }
```

- `orderType` is `BUY` or `SELL`. IDs and quantity must be positive; the UI sends whole shares.
- For a BUY, the server prices the order at the current ask from `MarketDataService`, rounded to 4 decimals, and uses that for both the cash check and the saved `pricePerUnit`. Any `pricePerUnit` the client sends is ignored.
- For a SELL, holdings-service checks the holding before anything is saved. Browser prices are ignored. The server saves a bid-price execution intent before settlement.
- Placing an order saves it as `SUBMITTED` and writes its submission audit trail: `SUBMITTED`, one `RULE_CHECKED` per placement check, then `VALIDATED` ([C2](C2-audit.md#the-submission-trail)). The response remains `SUBMITTED`; a background worker subsequently accepts and executes the order. Placement does not reserve cash or shares. Settlement serializes account updates and rechecks availability; competing orders may be rejected at execution.

**Response `201`** (the order DTO, also returned by every read below):

```json
{
  "success": true, "reason": null, "code": null,
  "orderId": 42, "accountId": 7, "instrumentId": 5, "orderType": "BUY", "quantity": 1,
  "pricePerUnit": 244.2366, "orderStatus": "SUBMITTED", "rejectionReason": null,
  "submittedAt": "2026-09-21T10:30:00", "acceptedAt": null, "filledAt": null,
  "quoteSource": null, "quoteTime": null,
  "createdAt": "2026-09-21T10:30:00", "updatedAt": "2026-09-21T10:30:00"
}
```

`quoteSource` and `quoteTime` describe the quote the order was priced from at execution: the feed it came from, and when the feed produced it (ISO-8601 UTC, e.g. `2026-09-21T10:30:04.512Z`). Both are null until execution prices the order; from then on `pricePerUnit` is that quote's price, and `filledAt` is the execution time.

**Response header `X-Request-Id`.** A UUID the server issues for the submission, on the `201` and on every refusal below that reaches placement (not on a `401` or an invalid-fields `400`). The submission's audit trail is filed under it ([C2](C2-audit.md#the-submission-trail)).

**Failures.** A refused order saves no order, but its submission and the checks that ran are audited under its request ID.

| HTTP | When | Body |
|---|---|---|
| 400 | The client or account may not trade | `success: false, code: ACCOUNT_RESTRICTED, reason` |
| 400 | Suspended stock | `success: false, code: NOT_TRADABLE, error` |
| 400 | Not enough shares (SELL) | `success: false, code: INSUFFICIENT_HOLDINGS, error` |
| 400 | Not enough cash (BUY) | `success: false, code: INSUFFICIENT_CASH, reason` |
| 400 | No usable quote (BUY) | `success: false, code: PRICE_UNAVAILABLE, reason` |
| 400 | Stale quote (BUY) | `success: false, code: STALE_QUOTE, reason` |
| 400 | Residence restricted by platform settings | `success: false, code: LOCATION_RESTRICTED, reason` |
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

The two account-history endpoints accept these optional date-filter parameters:

- `date`: a client-local calendar day (`YYYY-MM-DD`), month (`YYYY-MM`), or year (`YYYY`).
- `timeZone`: an IANA time-zone ID such as `America/New_York` or `UTC`.
- Supply both parameters together, or omit both to return the complete history. Clearing the UI filter omits both.
- Filtering uses `submittedAt`, includes the selected period's local start, and excludes the next period's local start. Converting both boundaries separately preserves daylight-saving transitions.
- No matches return `200 []`. Invalid dates, unknown or blank zones, and incomplete parameter pairs return `400 { "message": "..." }`.
- Ownership is checked before querying. The status endpoint combines its status restriction with the same date period.

The dashboard sends the browser's IANA time zone with every complete selection, cancels an older history request when the selection changes, and interprets offset-free order timestamps as UTC before local display. Status chips and pagination apply to the returned period; the dashboard's open-order total remains account-wide.

### Trade search (TRADING_OPS only)

`GET /api/v1/orders/trades/search` (buy-sell-service, through the existing orders gateway route).
Supply exactly one search mode:

- `?orderId=42`: the filled order with that ID.
- `?clientId=7&from=2026-01-01&to=2026-01-31`: that client's filled orders across their accounts.

IDs must be positive integers. `from` and `to` are UTC calendar dates (`YYYY-MM-DD`, positive
four-digit year), both required for client search. Filtering uses `filledAt`, from midnight on
`from` through the entire `to` day (exclusive next midnight). Results are newest fill first,
then descending order ID for equal fill times. Client IDs are not account IDs.

**Response `200`** is an array of [TradeSearchResult](../services/buy-sell-service/src/main/java/com/lemarketjames/trades/TradeSearchResult.java):

```json
[
  { "orderId": 42, "clientId": 7, "accountId": 9, "instrumentId": 5,
    "symbol": "AAPL", "side": "BUY", "quantity": 2, "pricePerUnit": 123.4567,
    "filledAt": "2026-01-10T12:00:00" }
]
```

Only FILLED orders are trades. Unknown IDs, unfilled orders and ranges with no matches return
`200 []`. Results use stored execution prices and instrument symbols, without live quote calls.
Mixed or incomplete search modes, malformed IDs/dates, nonpositive IDs, and reversed ranges
return `400 { "message": "..." }`. Missing/expired authentication gets `401`; every other role,
including a client searching for their own trade, gets `403`. Operations may search any client's
trades, including closed clients. Existing ownership rules for client history are unchanged.

The frontend `/trade-search` page provides separate order-ID and client/date search modes.
Trading Operations land there after login and see a navigation link in the signed-in layout.
Other signed-in roles are redirected to `/access-denied`. The screen labels dates and execution
times as UTC, preserves four-decimal execution prices, and clears/cancels stale searches when
criteria change. Component tests mock this endpoint; normal use calls the gateway.

### Changing an order's status (TRADING_OPS only)

| Method & path | Effect |
|---|---|
| `PUT /api/v1/orders/{orderId}/status/{status}` | Moves the order to `status` if its lifecycle allows it. Requesting `FILLED` uses the same live-pricing and durable settlement coordinator as automatic execution; the response reflects its outcome (FILLED, REJECTED, or waiting for market open). SUBMITTED cannot skip acceptance. |
| `POST /api/v1/orders/{orderId}/reject?reason=CODE` | Rejects an open order. `reason` is a RejectionReason code; the default is `REJECTED_BY_OPERATIONS`. |

- Clients get `403` from both endpoints, even for their own orders.
- A move the lifecycle doesn't allow gets `409 { "success": false, "code": "INVALID_STATUS_TRANSITION", "error" }`.
- An unknown reason code gets `400`.

### Automatic execution and recovery

The worker processes persisted open orders after startup and on each poll. It accepts SUBMITTED
orders, delays accepted orders while their exchange is closed, and executes eligible orders at
the fresh ask (BUY) or bid (SELL), rounded to four decimals. Unavailable/invalid quotes reject
with `PRICE_UNAVAILABLE`; stale quotes reject with `STALE_QUOTE`. A quote is stale once it is
older than the staleness limit ([C5](C5-config.md)); a rejected order is never priced from the
unusable quote. Tradability, account status,
and configured residence restrictions are rechecked at execution. BUY placement also refuses
stale quotes; residence restrictions apply to both sides at placement.

The quote used is stored with the fill: its price in `price_per_unit`, its feed in `quote_source`
and its time in `quote_time` ([013](../database/schema/013_execution_quote.sql)); `filled_at` is
the execution time. The source is the feed name defined in
[MarketOrderExecutor](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/MarketOrderExecutor.java).

Before sending a fill, buy-sell commits `PENDING`, its execution quote, and `settlement_pending`.
That flag prevents operations from changing/rejecting an order with an uncertain settlement.
Holdings locks the account and persists one outcome per order in `settlement_receipts`, atomically
with any cash/share changes. `POST /internal/holdings/settle` returns `200 { rejectionReason }`:
null for success, otherwise `INSUFFICIENT_CASH` or `INSUFFICIENT_HOLDINGS`. Repeating an identical
request returns that original outcome; changed payloads get 409. Malformed requests get 400.
Network errors and unknown failures retain the intent for retry without repricing. A confirmed
outcome becomes FILLED or REJECTED; a subsequent worker sees the final status and does nothing.

Cash movements use USD cents (HALF_UP); quantities and prices support at most four decimals.
The dashboard polls while orders are open and refreshes holdings/history when statuses change.
Holdings reads order history and buying-power data from buy-sell, never core.

## Holdings, balance, profile, trades (holdings-service)

| Method & path | Returns |
|---|---|
| `GET /api/v1/holdings` (alias `/api/holdings`) | `{ success, holdings: [{ symbol, quantity, averageCost, currentPrice, totalCost, currentValue, gainLoss, gainLossPercent }] }` for the caller. An empty portfolio is `200` with `holdings: []`, never a 404. |
| `POST /api/v1/holdings/validate` | Body `{ accountId, instrumentId, sellQuantity }` → `200 { success: true, message }`, or `400 { success: false, error }` when there aren't enough shares |
| `GET /api/balance` (aliases `/api/v1/balance`, `/api/v1/portfolio`) | `{ success, balance: { cash, invested, totalValue, buyingPower, dayGainLoss, dayGainLossPercent, totalGainLoss, totalGainLossPercent, currency } }` for the caller |
| `GET /api/v1/profile` | `{ username, fullName, email, phone, accountId, cashBalance, currency, tradingEnabled, openedDate }` for the caller |
| `GET /api/v1/trades?accountId=…` | the account's filled orders: `[{ symbol, side, quantity, pricePerUnit, filledAt }]` |

Holdings, balance and profile take no `accountId`; they are scoped to the caller's own account. A login without an account (staff) gets `404 { "success": false, "error": "Account not found", "code": "ACCOUNT_NOT_FOUND" }` from all three.

Holding fields: `averageCost` is the average price paid per share, from settlement's cost basis. `currentPrice` is the latest market-service quote. `totalCost` = quantity × averageCost, `currentValue` = quantity × currentPrice, `gainLoss` = currentValue − totalCost, `gainLossPercent` = gainLoss / totalCost × 100.

How the balance fields are computed:

| Field | Rule |
|---|---|
| `cash` | `accounts.cash_balance` |
| `buyingPower` | `cash` minus the cost of the account's open BUY orders (open = not final, see `OrderStatus.isOpen` in [Order.java](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/entity/Order.java)) |
| `invested` | Σ holding quantity × current price |
| `totalValue` | `cash + invested` |
| `dayGainLoss(%)` | Σ quantity × (price − today's open); % relative to Σ quantity × open |
| `totalGainLoss(%)` | the holdings totals, relative to total cost |

## Quotes (core-service, reading market-service)

| Method & path | Returns |
|---|---|
| `GET /api/quotes/{symbol}` | `{ success: true, quote }`, `404 { success: false, error: "Symbol not found" }`, or `503 { success: false, error: "Price unavailable" }` while the feed is down ([C4](C4-quote-feed.md)) |
| `GET /api/quotes` | `{ success: true, quotes: [quote…] }` sorted by symbol, or `503 { success: false, error: "Price unavailable" }` while the feed is down |

`quote` = `{ symbol, name, price, priceChange, priceChangePercent, highPrice, lowPrice, openPrice, volume, marketCap, peRatio, dividendYield, lastUpdate }`.

Feed-down detection is tracked by `MarketFeedStatus` (updated by every `MarketDataClient` call); the feed resumes automatically on the next successful call, with the outage's start and end logged ([C4](C4-quote-feed.md)).

## Sessions (core-service)

`POST /api/sessions/validate?accountId=…` with header `Authorization: Bearer <jwt>` returns `200 { success: true, session: { sessionId, accountId, expiresAt, isActive }, message }`, or `401 { success: false, message }` once the token has expired.

## Internal endpoints (service-to-service only, never through the gateway)

| Method & path | Caller → callee | Purpose |
|---|---|---|
| `GET /api/market/quotes`, `/quotes/{ticker}`, `/quotes/by-instrument/{id}` | core, buy-sell, holdings → market-service | Raw `QuoteSnapshot`s. Answers `503` while the feed is UNAVAILABLE ([C4](C4-quote-feed.md)). |
| `POST /internal/holdings/settle` | buy-sell → holdings-service | Applies a fill's cash and holdings changes, and audits `SETTLED` |
| `POST /internal/holdings/validate` | buy-sell → holdings-service | SELL holdings check at placement |
| `/internal/market/control/**` | tests → market-service | Quote-feed test controls, only when enabled; see [C4](C4-quote-feed.md) |

## Internal events and the execution interface (buy-sell-service)

- **Events.** [OrderStatusChanged](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderStatusChanged.java) is published after every status transition. [OrderFilled](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderFilled.java) is published in addition when an order fills, and carries the quote used (price, source, quote time) and the fill time. Both are in-process Spring events. Listen with `@EventListener`, or with `@TransactionalEventListener` to act only after the transition commits.
- **Outbound events (Kafka stub).** After the transition commits, [OrderEventForwarder](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventForwarder.java) hands both events to an [OrderEventPublisher](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventPublisher.java), keyed by order id; the topic names are defined in the forwarder. The only implementation today is [KafkaStubOrderEventPublisher](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/KafkaStubOrderEventPublisher.java), which logs the record and sends nothing, so events still do not reach other services.
- **Execution interface.** [OrderExecutor](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/OrderExecutor.java) takes an accepted order and returns an [ExecutionResult](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/ExecutionResult.java): fill at a quote ([QuoteUsed](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/QuoteUsed.java)), reject with a reason, or wait. The executor never changes status itself. Its caller applies the result through the normal transitions, so audit, events and settlement always happen the same way.
- **Reporting data source.** The `reporting_trades` view ([010](../database/schema/010_shared_contracts.sql)) has one row per filled order, with the client's segment, symbol, side, quantity, price, gross amount and times. Reports and insights read from it rather than joining the order tables themselves.

## Planned (agreed, not built)

These are contracts for upcoming stories. Build them as written, or update this section in the same PR.

- **Audit (COMPLIANCE):** `GET /api/v1/audit?orderId=…|requestId=…|clientId=…&from=&to=` → `{ success, events: [{ eventType, orderId, accountId, clientId, requestId, occurredAt, details }] }`, oldest first, online events only ([C2](C2-audit.md), [C5](C5-config.md) retention). `requestId` is how a refused order's trail is found; `orderId` and `accountId` can be null on its events.
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
