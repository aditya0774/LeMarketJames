# C6 – API and event contracts

Every REST endpoint, the internal events, the execution interface and the reporting data source. Front-end and back-end halves of a story meet here. Each endpoint below is checked against its controller; **Planned** marks endpoints that are agreed but not built yet.

Value lists (order statuses, rejection codes, roles, audit event types) are not repeated here. Each links to the one place it is defined.

## Rules for every public endpoint

- **Entry point.** Browsers call only a gateway, under `/api/**`: the trading app calls the trading gateway (`:8080` in a container, `:8089` on the host) and the staff app calls the staff gateway (`:8090`). Paths under `/internal/**` exist only service-to-service and are never routed by either gateway. See [Which gateway serves which paths](#which-gateway-serves-which-paths).
- **Authentication.** Log in with `POST /api/auth/login`; it sets an HTTP-only `jwt` cookie that every other endpoint reads. A missing or expired cookie gets `401`. The cookie carries the caller's roles ([C7](C7-roles.md)). Through the staff gateway the browser's cookie is named `staff_jwt` instead ([C7](C7-roles.md#the-staff-session-cookie)); the services see `jwt` either way.
- **Ownership.** Clients only see their own data. A foreign or unknown account or order id gets the same `403 { "success": false, "error": "Access denied", "code": "ACCOUNT_ACCESS_DENIED" }`, so ids never leak.
- **Money** is a JSON number in USD. **Order timestamps** represent UTC and currently serialize without an offset (`2026-09-21T10:30:00`); clients must interpret them as UTC. Session DTOs retain their existing local date-time format. Quote times are ISO-8601 UTC.
- **Validation failures** return `400 { "errors": { "<field>": "<message>" } }`. Other bad input returns `400 { "message": "..." }`.

## Which gateway serves which paths

Each gateway's route list is its configuration file; this table says what they add up to. A path a gateway has no route for answers `404` there, whoever asks.

| Paths | Service | Trading gateway ([application.yml](../services/gateway-service/src/main/resources/application.yml)) | Staff gateway ([application-staff.yml](../services/gateway-service/src/main/resources/application-staff.yml)) |
|---|---|---|---|
| `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` | auth-service | yes | yes |
| `POST /api/auth/register` | auth-service | yes | no: staff logins are not self-service |
| `GET /api/v1/orders/trades/search`, `GET /api/v1/orders/{orderId}/timeline` | buy-sell-service | yes, as part of `/api/v1/orders/**` | yes |
| The rest of `/api/v1/orders/**`, `/api/v1/buy-orders/**` | buy-sell-service | yes | no |
| Holdings, balance, profile, portfolio, trades | holdings-service | yes | no |
| `/api/market/**` | market-service | yes | no |
| `/api/v1/reports/**` | reporting-service | no: these paths fall through to core-service, which has no such endpoint | yes |
| Everything else under `/api/**` | core-service | yes | no |

- A gateway decides only which paths exist. Who may call them is each service's rule ([C7](C7-roles.md)), the same through either gateway: a `CLIENT` who signs in through the staff gateway gets `403` on every staff route.
- The session cookie has a different name at each gateway ([C7](C7-roles.md#the-staff-session-cookie)), so a session from one is not a session at the other.
- A new staff endpoint needs a route in `application-staff.yml` and a row here.

## Auth (auth-service)

| Method & path | Request | Success |
|---|---|---|
| `POST /api/auth/register` | `username, password, email, fullName, streetAddress, apartment?, city, state, zipCode, country, ssn, initialDeposit, investmentExperience ("beginner" / "experienced"), employmentStatus, dateOfBirth (yyyy-MM-dd), phoneNumber` | `201 { username, message }` |
| `POST /api/auth/login` | `{ "username": "<email>", "password": "..." }`: the email goes in `username` | `200 { username, roles, accountId?, message }` and the `jwt` cookie |
| `POST /api/auth/logout` | none | `200 { message }`; clears the cookie |
| `GET /api/auth/me` | none | `200 { username, roles, accountId? }` |

- Clients and staff sign in with the same endpoint, each through their own gateway. The staff gateway serves login, logout and `/me` only, and the cookie it sets and clears is `staff_jwt`.
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

### GET /api/v1/orders/stream

Live order-status events for the signed-in client, delivered as Server-Sent Events (`text/event-stream`).

| Method & path | Returns |
|---|---|
| `GET /api/v1/orders/stream` | SSE stream of order-status changes for the caller's own account only |

The stream emits an `order-status-changed` event each time buy-sell-service publishes an [OrderStatusChanged](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderStatusChanged.java) for the caller's account.

**Event payload:**

```json
{
  "orderId": 42,
  "accountId": 7,
  "from": "SUBMITTED",
  "to": "ACCEPTED",
  "occurredAt": "2026-09-21T10:30:01.123Z"
}
```

Access control and failures:

| HTTP | When | Body |
|---|---|---|
| 200 | Stream opened | `text/event-stream` |
| 401 | Not authenticated | see rules above |
| 403 | Authenticated but not a client role | see rules above |

- The endpoint is for signed-in clients only.
- The server infers the account from the authenticated caller; it does not accept an account-id parameter.
- The stream must never include events for another account.

### Order timeline (TRADING_OPS only)

Retrieve the complete audit trail for an order, showing all events in chronological order. This endpoint is **restricted to staff with the TRADING_OPS role** and is used to investigate order lifecycle and troubleshoot fills. The staff app reaches it through the staff gateway ([which gateway](#which-gateway-serves-which-paths)).

| Method & path | Returns |
|---|---|
| `GET /api/v1/orders/{orderId}/timeline` | an array of audit events for the order, oldest first |

**Response `200`:**

```json
[
  {
    "eventType": "SUBMITTED",
    "occurredAt": "2026-09-21T10:30:00Z",
    "details": {
      "side": "BUY",
      "quantity": 10,
      "price": 244.2366
    }
  },
  {
    "eventType": "VALIDATED",
    "occurredAt": "2026-09-21T10:30:01Z",
    "details": {
      "validationStatus": "PASSED"
    }
  },
  {
    "eventType": "ACCEPTED",
    "occurredAt": "2026-09-21T10:30:02Z",
    "details": {}
  },
  {
    "eventType": "FILLED",
    "occurredAt": "2026-09-21T10:30:03Z",
    "details": {
      "executionPrice": 244.2366,
      "quantity": 10
    }
  },
  {
    "eventType": "SETTLED",
    "occurredAt": "2026-09-21T10:30:04Z",
    "details": {
      "cashDelta": -2442.366,
      "quantityDelta": 10
    }
  }
]
```

**Access control and failures:**

| HTTP | When | Body |
|---|---|---|
| 200 | Success | array of events, empty if no events exist |
| 401 | Not authenticated | see rules above |
| 403 | Not TRADING_OPS role, or order not found | see ownership rules |

- An unknown or inaccessible order ID returns `403` (no information leak).
- Clients and other staff roles are denied with `403`, even if they own the order.
- Events are sourced exclusively from the audit trail ([C2](C2-audit.md)), ensuring immutable and comprehensive order history.

### Trade search (TRADING_OPS only)

`GET /api/v1/orders/trades/search` (buy-sell-service, through the trading gateway's orders route for the trading app's search screen, and through the staff gateway for the staff app).
Supply exactly one search mode:

- `?orderId=42`: the filled order with that ID.
- `?clientId=7`: all that client's filled orders across their accounts, without a date filter.
- `?clientId=7&from=2026-01-01&to=2026-01-31`: that client's filled orders across their accounts.

IDs must be positive integers. `from` and `to` are UTC calendar dates (`YYYY-MM-DD`, positive
four-digit year), supplied together or both omitted for client search. Filtering uses `filledAt`, from midnight on
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

`GET /api/v1/orders/trades/clients?name=Alex` provides a Trading Operations-only client-name
lookup through the staff gateway. It returns `[{ "clientId": 7, "fullName": "Alex Smith" }]`.
The trimmed name must contain 2–100 characters; matching is case-insensitive literal substring
matching (not wildcard matching). Up to 50 matches are returned, sorted by name then client ID.
No matches returns `[]`; invalid input gets `400`, missing authentication `401`, other roles `403`.
The staff UI shows each matching name with its ID and asks the operator to select a client,
then uses the existing client-ID search with an optional date range.

The staff frontend on port 4201 provides `/trade-search` with separate Order search and Client search options. Each accepts its corresponding ID or a client name. A unique name match loads trades automatically; duplicate names require a client selection. Client date filters are optional. Its same-origin `/api` calls target the staff gateway on 8090 (LMKT-143), never the
trading gateway. The staff gateway forwards login/logout, session checks, trade search and reports; owning services enforce roles.
Trading Operations land on their dashboard after login and reach it from a navigation link in the
signed-in layout. Other signed-in roles are redirected to `/access-denied` ([C7](C7-roles.md#the-staff-app)). The screen labels dates and execution
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

## Reports (reporting-service)

Aggregate reports for staff, on reporting-service (`:8086`). The trading gateway has no route to them: they are served via the staff gateway (`:8090`), the staff app's only backend entry point ([which gateway](#which-gateway-serves-which-paths)).

Rules for every report (the full list, for whoever builds one, is in the service's [README](../services/reporting-service/README.md#rules-for-every-report-endpoint)):

- **Path.** Every report is under `/api/v1/reports/...`.
- **Access.** `ANALYST` only ([C7](C7-roles.md)). A missing or expired cookie gets `401`; every other role gets `403`.
- **Data source.** Reports read only the `reporting_trades` view ([010](../database/schema/010_shared_contracts.sql)). They count filled orders: a *trade* is one filled order. There are no fees; amounts are quantity × price.
- **Aggregates only.** A response never contains `client_id` or anything else that identifies an individual client, and never a single trade.
- **Time zone.** A report's days, weeks, months and years are those of the reports time zone, the `lmj.reports.time-zone` setting ([C5](C5-config.md)), not UTC and not the browser's zone. Orders are stored in UTC; [ReportCalendar](../services/reporting-service/src/main/java/com/lemarketjames/reports/period/ReportCalendar.java) converts, including a period's local start and excluding the next period's. Weeks run Monday to Sunday.

### GET /api/v1/reports/ping

Reads no data. Tells a caller that reporting-service is up and that their role may read reports, and which time zone reports use.

```json
{ "success": true, "service": "reporting-service", "timeZone": "America/New_York" }
```

`timeZone` is the IANA ID of the reports time zone in this environment; the value above is an example.

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

- **Events.** buy-sell-service announces three things about an order: [OrderSubmitted](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderSubmitted.java) when it is placed, [OrderStatusChanged](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderStatusChanged.java) after every status transition, and [OrderFilled](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderFilled.java) in addition when it fills. [The order events](#the-order-events) below says when each is published and what it carries. All three are in-process Spring events. Listen with `@EventListener`, or with `@TransactionalEventListener` to act only after the order's transaction commits.
- **Outbound events (Kafka).** After the order's transaction commits, [OrderEventForwarder](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventForwarder.java) hands each event to an [OrderEventPublisher](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventPublisher.java). With Kafka switched on, as in Docker Compose and Jenkins, [KafkaOrderEventPublisher](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/KafkaOrderEventPublisher.java) publishes them to the broker, so other services can consume them:
  - **Topics.** One per event. Their names are constants in the forwarder; [OrderEventTopics](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventTopics.java) creates them on the broker when buy-sell-service starts.
  - **Key.** The order id, as text. Within a topic, one order's events share a partition, so a consumer reads them in the order they happened. Nothing orders one topic against another: a consumer of two topics can receive an order's first status change before its `OrderSubmitted`.
  - **Value.** The event record as plain JSON, UTF-8: the event's fields under their own names, enums as their names, amounts as JSON numbers, and times as ISO-8601 UTC text as in the REST API. There are no type headers, so a consumer needs none of this service's classes.
  - **Delivery is best effort.** An order is committed before its events are sent. If the broker can't be reached the event is logged as an error and dropped; nothing replays it later (there is no outbox). A consumer that must not miss a fill should reconcile against the REST API or the `reporting_trades` view.
  - **Without Kafka.** Where no broker runs (unit tests, the native Windows scripts), [KafkaStubOrderEventPublisher](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/KafkaStubOrderEventPublisher.java) logs each record and sends nothing. This is the default; the setting that switches is in [C5](C5-config.md#order-events).
- **Execution interface.** [OrderExecutor](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/OrderExecutor.java) takes an accepted order and returns an [ExecutionResult](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/ExecutionResult.java): fill at a quote ([QuoteUsed](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/QuoteUsed.java)), reject with a reason, or wait. The executor never changes status itself. Its caller applies the result through the normal transitions, so audit, events and settlement always happen the same way.
- **Reporting data source.** The `reporting_trades` view ([010](../database/schema/010_shared_contracts.sql)) has one row per filled order, with the client's segment, symbol, side, quantity, price, gross amount and times. Reports and insights read from it rather than joining the order tables themselves; for reports it is the only source ([Reports](#reports-reporting-service)).

### The order events

| Event | Published when | How often | Kafka topic |
|---|---|---|---|
| `OrderSubmitted` | A new order passes its placement checks and is saved. | Once per order, before any other event | `lemarket.orders.submitted` |
| `OrderStatusChanged` | An order moves from one status to another ([C1](C1-orders.md)). Placement isn't a move, so the first one is the order leaving `SUBMITTED`. | Once per transition | `lemarket.orders.status-changed` |
| `OrderFilled` | An order fills and is settled. It comes with that order's `OrderStatusChanged` to `FILLED`, not instead of it. | Once per filled order | `lemarket.orders.filled` |

An order that fills therefore produces one `OrderSubmitted`, one `OrderStatusChanged` for each status it passes through, and one `OrderFilled`. An order that is rejected produces the first two and no `OrderFilled`. A submission that is refused at placement is never saved, so it produces no event at all; it is in the audit trail only ([C2](C2-audit.md#the-submission-trail)).

**`OrderSubmitted`** tells a consumer that an order exists and what was ordered.

| Field | Type | Meaning |
|---|---|---|
| `orderId` | number | The order |
| `accountId` | number | The order's account |
| `instrumentId` | number | The stock ordered |
| `side` | text | `BUY` or `SELL` |
| `quantity` | number | Shares ordered |
| `price` | number or null | Price per share the order was placed at. Null for a SELL, which is priced only when it fills. A BUY can fill at a different price; `OrderFilled` has the one used. |
| `submittedAt` | time | When the order was placed |

**`OrderStatusChanged`** tells a consumer where an order is in its life. It doesn't say why an order was rejected; the order's `rejectionReason` in the REST API does.

| Field | Type | Meaning |
|---|---|---|
| `orderId` | number | The order |
| `accountId` | number | The order's account |
| `from` | text | Status before the transition ([C1](C1-orders.md)) |
| `to` | text | Status after it |
| `occurredAt` | time | When the transition happened |

**`OrderFilled`** carries what a portfolio or reporting consumer needs about a fill, so it doesn't have to read the order back.

| Field | Type | Meaning |
|---|---|---|
| `orderId` | number | The order |
| `accountId` | number | The order's account |
| `instrumentId` | number | The stock traded |
| `side` | text | `BUY` or `SELL` |
| `quantity` | number | Shares filled |
| `price` | number | Price per share, taken from the quote the order executed at |
| `filledAt` | time | When the fill happened |
| `quoteSource` | text | The feed that quote came from |
| `quoteTime` | time | When the feed produced that quote |

## Planned (agreed, not built)

These are contracts for upcoming stories. Build them as written, or update this section in the same PR.

- **Audit (COMPLIANCE):** `GET /api/v1/audit?orderId=…|requestId=…|clientId=…&from=&to=` → `{ success, events: [{ eventType, orderId, accountId, clientId, requestId, occurredAt, details }] }`, oldest first, online events only ([C2](C2-audit.md), [C5](C5-config.md) retention). `requestId` is how a refused order's trail is found; `orderId` and `accountId` can be null on its events.
- **Reports (ANALYST):** the service, its access rule and `GET /api/v1/reports/ping` are built; see [Reports](#reports-reporting-service), whose rules apply to the two reports below. These two are still planned. Their response shapes were agreed before those rules (`activity` lists single trades, and `instruments` needs prices that `reporting_trades` doesn't have), so the story that builds each one must first reconcile it with the rules and update this section:
  - `GET /api/v1/reports/activity?startDate=&endDate=&limit=50&offset=0` → `{ success, activities: [{ id, type, symbol, quantity, price, totalAmount, timestamp }], total, limit, offset }`. `startDate` and `endDate` are days in the reports time zone, both included.
  - `GET /api/v1/reports/instruments?sortBy=gainLoss|gainLossPercent|quantity|value&order=ASC|DESC` → `{ success, instruments: [{ symbol, quantity, totalValue, gainLoss, gainLossPercent, performance: { week, month, threeMonth, year }, volatility, beta }] }`.
- **Order symbol fields:** add `symbol` and `instrumentName` to the order DTO, taken from `instruments`. This is non-breaking and lets `orders-panel.ts` stop resolving ids itself.
- **Candles:** `GET /api/v1/instruments/{symbol}/candles?limit=60` (max 390) → `{ success, symbol, interval: "1m", candles: [{ time, open, high, low, close, volume }] }` from `price_candles`, oldest first. An unknown symbol gets `404` in the quotes shape.
- **Watchlist:** `GET /api/v1/watchlist`, `POST /api/v1/watchlist { symbol }` (`409 ALREADY_WATCHED`), `DELETE /api/v1/watchlist/{symbol}`. It needs a new `watchlist (account_id, instrument_id, added_at)` table in the next free migration number, in its own `watchlist` feature package.

## Changing a contract

- Endpoint paths and methods, request fields and response fields are fixed once published. Additive, backward-compatible changes are fine.
- Removing or renaming anything needs team agreement first.
- Change this file in the same PR as the code, so it never describes something the code doesn't do.
