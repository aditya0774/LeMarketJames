# C6 – API and event contracts

Every REST endpoint, the internal events and the services that consume them, the execution interface and the reporting data source. Front-end and back-end halves of a story meet here. Each endpoint below is checked against its controller; **Planned** marks endpoints that are agreed but not built yet.

Value lists (order statuses, rejection codes, roles, audit event types) are not repeated here. Each links to the one place it is defined.

## Rules for every public endpoint

- **Entry point.** Browsers call only a gateway, under `/api/**`: the trading app calls the trading gateway (`:8080` in a container, `:8089` on the host) and the staff app calls the staff gateway (`:8090`). Paths under `/internal/**` exist only service-to-service and are never routed by either gateway. See [Which gateway serves which paths](#which-gateway-serves-which-paths).
- **Authentication.** Log in with `POST /api/auth/login`; it sets an HTTP-only `jwt` cookie that every other endpoint reads. A missing or expired cookie gets `401`. A valid cookie whose account has since been closed, suspended, expired, deleted or (for staff) deactivated gets `401 { "success": false, "code": "ACCOUNT_INACTIVE", "error" }` on its next request: every servlet service checks the account on each request in the shared `JwtAuthenticationFilter`. `/internal/**` calls are never refused this way. The cookie carries the caller's roles ([C7](C7-roles.md)). Through the staff gateway the browser's cookie is named `staff_jwt` instead ([C7](C7-roles.md#the-staff-session-cookie)); the services see `jwt` either way.
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
| `/api/v1/notifications/**` | notification-service | yes | no |
| `/api/v1/market-activity/**` | activity-service | yes | no |
| `/api/v1/surveillance/**` | surveillance-service | no: these paths fall through to core-service, which has no such endpoint | yes |
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
- Login fails with `400 { message }` for a wrong email or password, or a locked login ([C5](C5-config.md) lockout).
- The right password for a login that may no longer be used gets `403 { "success": false, "code": "ACCOUNT_INACTIVE", "error", "message" }`, with both texts "This account is no longer active. Please contact the firm." That is any client that isn't `ACTIVE` ([AccountStatus](../libs/common/src/main/java/com/lemarketjames/common/domain/AccountStatus.java)) or a staff login whose `active` flag is off. Match on `code` ([AccountInactiveException](../libs/common/src/main/java/com/lemarketjames/common/security/AccountInactiveException.java)), not the text. A wrong password never gets this response, so it never reveals an account's status, and an account that no longer exists gets the wrong-password `400`.
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
  "orderId": 42, "accountId": 7, "instrumentId": 5,
  "symbol": "AAPL", "instrumentName": "BronApple",
  "orderType": "BUY", "quantity": 1,
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
- `page`: zero-based page number (default `0`).
- `size`: page size from `1` to `100` (default `20`).
- Supply both parameters together, or omit both to return the complete history. Clearing the UI filter omits both.
- Filtering uses `submittedAt`, includes the selected period's local start, and excludes the next period's local start. Converting both boundaries separately preserves daylight-saving transitions.
- Results are ordered newest-first by `submittedAt`, then `orderId` as a tie-breaker.
- No matches return `200 []`. Invalid dates, unknown or blank zones, incomplete parameter pairs, and invalid pagination values return `400`.
- Ownership is checked before querying. The status endpoint combines its status restriction with the same date period.

The order DTO now includes additive stock display fields:

- `symbol`: instrument ticker from `instruments` (e.g. `AAPL`).
- `instrumentName`: instrument display name from `instruments` (e.g. `BronApple`).

`instrumentId` remains part of the DTO for stable identity and backward compatibility.

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
  "occurredAt": "2026-09-21T10:30:01.123Z",
  "rejectionReason": null
}
```

The fields are those of [`OrderStatusChanged`](#the-order-events): `rejectionReason` is a rejection code when `to` is `REJECTED`, and null otherwise.

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

The staff frontend on port 4201 provides `/trade-search` with separate Order search and Client search options. Each accepts its corresponding ID or a client name. A unique name match loads trades automatically; duplicate names require a client selection. Client date filters are optional. Selecting a result's order number shows that order's audit timeline from [`GET /api/v1/orders/{orderId}/timeline`](#order-timeline-trading_ops-only). Its same-origin `/api` calls target the staff gateway on 8090 (LMKT-143), never the
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
Network errors and unknown failures retain the intent for retry without repricing. Recovery is
automatic: the execution worker retries persisted open orders after startup and on each poll,
including orders already marked `PENDING` with `settlement_pending=true`.
A confirmed outcome becomes FILLED or REJECTED; subsequent workers see the final status and do
nothing.

Cash movements use USD cents (HALF_UP); quantities and prices support at most four decimals.
The dashboard opens `GET /api/v1/orders/stream` for live order-status events and refreshes
orders, holdings and history whenever a status-change event arrives.
Holdings reads order history and buying-power data from buy-sell, never core.

## Holdings, balance, profile, trades (holdings-service)

| Method & path | Returns |
|---|---|
| `GET /api/v1/holdings` (alias `/api/holdings`) | `{ success, holdings: [{ symbol, quantity, averageCost, currentPrice, totalCost, currentValue, gainLoss, gainLossPercent }] }` for the caller. An empty portfolio is `200` with `holdings: []`, never a 404. |
| `POST /api/v1/holdings/validate` | Body `{ accountId, instrumentId, sellQuantity }` → `200 { success: true, message }`, or `400 { success: false, error }` when there aren't enough shares |
| `GET /api/balance` (aliases `/api/v1/balance`, `/api/v1/portfolio`) | `{ success, balance: { cash, invested, totalValue, buyingPower, dayGainLoss, dayGainLossPercent, totalGainLoss, totalGainLossPercent, currency } }` for the caller |
| `POST /api/v1/portfolio/deposits` | Body `{ amount, requestId }` → `200 { success: true, balance }` after adding cash |
| `POST /api/v1/portfolio/withdrawals` | Body `{ amount, requestId }` → `200 { success: true, balance }` after removing available cash |
| `GET /api/v1/profile` | `{ username, fullName, email, phone, accountId, cashBalance, currency, tradingEnabled, openedDate }` for the caller |
| `GET /api/v1/trades?accountId=…` | the account's filled orders: `[{ symbol, side, quantity, pricePerUnit, filledAt }]` |
| `GET /api/v1/portfolio/history?range=1D` | `{ success: true, range, points: [{ time, value }] }`: what the caller's stocks were worth over the [chart range](#chart-ranges), oldest first; `400 { success: false, error: "Unknown range" }` for any other range |

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

### Deposit and withdraw cash

Both endpoints are scoped to the account resolved from the caller's JWT. A staff login has no
account and gets `404 { success: false, code: "ACCOUNT_NOT_FOUND", error: "Account not found" }`.
The success `balance` has exactly the `GET /api/balance` shape, so the app can show the new buying
power without another request.

- `amount` is USD, above zero with at most two decimals and no more than the
  [C5](C5-config.md) per-movement limit. A refusal is
  `400 { success: false, code: "INVALID_AMOUNT", error }`.
- A withdrawal may take at most `buyingPower`, leaving the cash reserved by open BUY orders in the
  account. A refusal is `400 { success: false, code: "INSUFFICIENT_FUNDS", error }`. If open orders
  cannot be read, the withdrawal fails without changing cash; an outage never counts as no orders.
- `requestId` is a UUID made by the app for an attempt and retained for its retries. The first call
  stores its result; another call with the same ID returns successfully without moving cash again.
- The balance change, idempotency receipt and `CASH_DEPOSITED` or `CASH_WITHDRAWN` audit event
  commit in one transaction. The event contains `amount`, `balanceAfter` and `requestId` ([C2](C2-audit.md)).

## Reports (reporting-service)

Aggregate reports for staff, on reporting-service (`:8086`). The trading gateway has no route to them: they are served via the staff gateway (`:8090`), the staff app's only backend entry point ([which gateway](#which-gateway-serves-which-paths)).

Rules for every report (the full list, for whoever builds one, is in the service's [README](../services/reporting-service/README.md#rules-for-every-report-endpoint)):

- **Path.** Every report is under `/api/v1/reports/...`.
- **Access.** `ANALYST` only ([C7](C7-roles.md)). A missing or expired cookie gets `401`; every other role gets `403`.
- **Data source.** Reports read only the `reporting_trades` view ([010](../database/schema/010_shared_contracts.sql)). They count filled orders: a *trade* is one filled order. There are no fees; amounts are quantity × price. The one exception is the [overnight reports list](#get-apiv1reportsovernight), which is about report runs, not trades, and reads `report_runs`.
- **Aggregates only.** A response never contains `client_id` or anything else that identifies an individual client, and never a single trade.
- **Time zone.** A report's days, weeks, months and years are those of the reports time zone, the `lmj.reports.time-zone` setting ([C5](C5-config.md)), not UTC and not the browser's zone. Orders are stored in UTC; [ReportCalendar](../services/reporting-service/src/main/java/com/lemarketjames/reports/period/ReportCalendar.java) converts, including a period's local start and excluding the next period's. Weeks run Monday to Sunday.

### GET /api/v1/reports/ping

Reads no data. Tells a caller that reporting-service is up and that their role may read reports, and which time zone reports use.

```json
{ "success": true, "service": "reporting-service", "timeZone": "America/New_York" }
```

`timeZone` is the IANA ID of the reports time zone in this environment; the value above is an example.

### GET /api/v1/reports/activity

Trading activity grouped by the client's segment (LMKT-43), for `ANALYST` only.
The staff app calls this through the staff gateway.

- `startDate`, `endDate`: optional ISO calendar dates (`YYYY-MM-DD`), both inclusive in
  the configured reports time zone. Omitted boundaries use the defaults defined in
  [SegmentActivityService](../services/reporting-service/src/main/java/com/lemarketjames/reports/service/SegmentActivityService.java).
- Filtering uses `filled_at`, converted to a half-open UTC range by `ReportCalendar`.
- One row per segment with matching trades, sorted by segment name; no matching trades returns
  `200 { "success": true, "data": [] }`. Segments without matching trades are omitted.
- `activeClients` counts distinct clients across all their accounts, `tradeCount` counts filled
  orders, and `totalTradedValue` sums `gross_amount` for both buys and sells in USD, without
  rounding the stored amounts.
- Segment names come from the view, with sample definitions supplied by [C3](C3-seed-data.md).
  The API has no hard-coded segment list.
- Responses contain only the four aggregate fields below. No client, account, order, name or
  contact information is returned; there is no individual-client filtering or drill-down.
- Malformed or reversed dates return `400`; missing/invalid authentication returns `401`;
  non-Analyst roles (including `CLIENT` and `TRADING_OPS`) return `403`.

```json
{
  "success": true,
  "data": [
    { "segment": "RETAIL", "activeClients": 42, "tradeCount": 180, "totalTradedValue": 215090.1250 }
  ]
}
```

The report target is under ten seconds. The PostgreSQL integration test exercises an explicit
100,000-trade synthetic baseline through query execution and response serialization. Production
capacity and concurrency require measurement on the deployed dataset and hardware.

### GET /api/v1/reports/activity/comparison

Analyst-only segment trends, using the same view, date semantics and safeguards as activity.
The original activity endpoint retains its aggregate-only response shape.

- `startDate`, `endDate`: selected inclusive range; defaults are shared with
  [SegmentActivityService](../services/reporting-service/src/main/java/com/lemarketjames/reports/service/SegmentActivityService.java).
- `comparisonStartDate`, `comparisonEndDate`: supply both or neither. When omitted,
  compare with the immediately preceding range of the same number of calendar days.
  Custom ranges can overlap or differ in duration; totals are not normalized by duration.
- Response: `success`, `data` (selected range's existing segment rows), `startDate`, `endDate`,
  `comparisonStartDate`, `comparisonEndDate`, `timeZone`, `comparison`, and `allTimeData`.
- `allTimeData` contains segment activity rows across the entire reporting view, independent
  of both date ranges. Clients are counted distinctly across all history, not summed across
  periods. These are clients with at least one filled trade, not all registered clients.
  The lower summary cards and segment share chart use this all-time data.
- Each comparison row contains `segment`, `current` and `previous` (each a segment activity row),
  and `activeClients`, `tradeCount`, `totalTradedValue` changes with `absolute` and `percent`.
  Absolute change is selected minus comparison; percentage is relative to comparison,
  rounded to two decimals, or null when the comparison value is zero.
- Rows are the alphabetically sorted union of segments active in either range. A missing
  segment in one range has zero metrics. Both ranges empty returns empty arrays.
- Amounts are USD gross traded values, not profit. No individual identifiers are returned.
- Invalid/reversed ranges or incomplete comparison dates return 400; unauthenticated
  callers receive 401 and other roles receive 403.

### GET /api/v1/reports/trades

Trade aggregation report, grouped by period (day, week, month, or year). Responds with counts and totals per period, newest first.

**Parameters** (all optional except `periodType`):

- `periodType` (required): `DAY`, `WEEK`, `MONTH`, or `YEAR`
- `from` (optional): start date in `YYYY-MM-DD` format; defaults to 365 days ago
- `to` (optional): end date in `YYYY-MM-DD` format; defaults to today
- `timeZone` (optional): IANA time zone ID (e.g., `America/New_York`); defaults to the reports time zone

Dates are interpreted in the specified time zone. The `from` day is included; the `to` day is included. Date validation: `from` may not be after `to`.

**Response `200`:**

```json
{
  "data": [
    {
      "period": "2026-10-01",
      "tradeCount": 5,
      "buyCount": 3,
      "sellCount": 2,
      "totalValue": 1250.50
    },
    {
      "period": "2026-10-02",
      "tradeCount": 2,
      "buyCount": 1,
      "sellCount": 1,
      "totalValue": 500.00
    }
  ],
  "generatedAt": "2026-10-07T20:15:30.123Z"
}
```

Period format depends on `periodType`:
- `DAY`: `YYYY-MM-DD` (e.g., `2026-10-01`)
- `WEEK`: `YYYY-Www` ISO 8601 week format (e.g., `2026-W40` for week 40)
- `MONTH`: `YYYY-MM` (e.g., `2026-10`)
- `YEAR`: `YYYY` (e.g., `2026`)

Each period aggregates:
- `tradeCount`: total number of filled orders (trades)
- `buyCount`: number of BUY orders
- `sellCount`: number of SELL orders
- `totalValue`: sum of gross amounts (quantity × price), all orders

**Failures:**

| HTTP | When | Body |
|---|---|---|
| 400 | Missing `periodType`, invalid format, or `from` > `to` | `{ "message": "..." }` |
| 401 | Not authenticated | see access rules above |
| 403 | Not ANALYST role | see access rules above |

### GET /api/v1/reports/insights/volume

Insight: the traded volume of the last completed period compared with the period before it. Computed from `reporting_trades` on every request (nothing is cached), so the figures move forward by themselves as each day in the reports time zone ends. `current` and `previous` are whole periods, so `change` never compares an unfinished period with a finished one (it would always look like a drop). The period still running is reported separately as `inProgress`, so a trade made now shows up on the next request. The staff tile no longer calls this endpoint: it uses [`/volume/compare`](#get-apiv1reportsinsightsvolumecompare).

**Parameters:**

- `periodType` (required): `DAY`, `WEEK`, `MONTH` or `YEAR`, in any letter case. The periods are those of [ReportCalendar](../services/reporting-service/src/main/java/com/lemarketjames/reports/period/ReportCalendar.java): the day before today in the reports time zone for `DAY`, the last Monday-to-Sunday week for `WEEK`, and so on.

**Response `200`:**

```json
{
  "periodType": "WEEK",
  "current":  { "startDate": "2026-10-05", "endDate": "2026-10-11", "tradeCount": 40, "shareVolume": 1200, "grossAmount": 54000.00 },
  "previous": { "startDate": "2026-09-28", "endDate": "2026-10-04", "tradeCount": 32, "shareVolume": 1000, "grossAmount": 41000.00 },
  "inProgress": { "startDate": "2026-10-12", "endDate": "2026-10-12", "tradeCount": 3, "shareVolume": 75, "grossAmount": 900.00 },
  "change":   { "shareVolume": 200, "shareVolumePercent": 20.00, "grossAmount": 13000.00, "grossAmountPercent": 31.71 },
  "generatedAt": "2026-10-12T14:00:00Z"
}
```

- `startDate` and `endDate` are both included, days in the reports time zone. For `inProgress`, `endDate` is today.
- `shareVolume` is the sum of the trades' quantities; `grossAmount` is quantity × price ([no fees](#reports-reporting-service)).
- `change` is `current` minus `previous`. A `*Percent` is relative to `previous`, rounded to two decimals, and `null` when `previous` is `0` (there is nothing to compare with). A period with no trades returns zeros, not an error.

**Failures:**

| HTTP | When | Body |
|---|---|---|
| 400 | `periodType` missing or not one of the four values | `{ "message": "..." }` |
| 401 | Not authenticated | see access rules above |
| 403 | Not ANALYST role (`CLIENT` and `TRADING_OPS` included) | see access rules above |

### GET /api/v1/reports/insights/volume/compare

The traded volume of two date ranges the caller chooses, compared. The staff tile uses it for its Today, This week and Last week cards (each against the period just before it) and for the ranges an analyst picks. Computed from `reporting_trades` on every request, like the insight above.

**Parameters** (all four required, `YYYY-MM-DD`, days in the reports time zone):

- `currentFrom`, `currentTo`: the range being looked at, both days included.
- `previousFrom`, `previousTo`: the range it is compared with, both days included. The ranges may overlap and need not be the same length.

**Response `200`:**

```json
{
  "current":  { "startDate": "2026-10-12", "endDate": "2026-10-14", "tradeCount": 9, "shareVolume": 420, "grossAmount": 15000.00 },
  "previous": { "startDate": "2026-10-05", "endDate": "2026-10-07", "tradeCount": 6, "shareVolume": 300, "grossAmount": 11000.00 },
  "change":   { "shareVolume": 120, "shareVolumePercent": 40.00, "grossAmount": 4000.00, "grossAmountPercent": 36.36 },
  "generatedAt": "2026-10-14T14:00:00Z"
}
```

`current`, `previous` and `change` follow the rules of the insight above. A range that includes today counts the trades made so far, so a trade made now shows up on the next request.

**Failures:**

| HTTP | When | Body |
|---|---|---|
| 400 | A date is missing or not `YYYY-MM-DD`, or a `*From` is after its `*To` | `{ "message": "..." }` |
| 401 | Not authenticated | see access rules above |
| 403 | Not ANALYST role (`CLIENT` and `TRADING_OPS` included) | see access rules above |

### GET /api/v1/reports/insights/active-clients

Insight: how many distinct clients traded on each of the last 30 days (LMKT-48), for `ANALYST` only. Computed from `reporting_trades` on every request (nothing is cached).

**Parameters:** none.

**Response `200`:**

```json
{
  "startDate": "2026-09-15",
  "endDate": "2026-10-14",
  "days": [
    { "date": "2026-09-15", "activeClients": 12 },
    { "date": "2026-09-16", "activeClients": 0 }
  ],
  "generatedAt": "2026-10-14T14:00:00Z"
}
```

- The window is the 30 days ending today in the reports time zone, both ends included; `days` lists every one of them, oldest first, and a day without trades is `0`, not missing. Today counts the trades made so far.
- `activeClients` is the number of distinct clients with at least one filled trade that day, counted across all of a client's accounts. Each day is a range from [ReportCalendar](../services/reporting-service/src/main/java/com/lemarketjames/reports/period/ReportCalendar.java).
- Counts only: the response never contains a client, account or order identifier.

**Failures:**

| HTTP | When | Body |
|---|---|---|
| 401 | Not authenticated | see access rules above |
| 403 | Not ANALYST role (`CLIENT` and `TRADING_OPS` included) | see access rules above |

### GET /api/v1/reports/overnight

The overnight reports list (LMKT-45). Heavy reports run each night at the overnight report time, outside trading hours, and each run is recorded here with its status, when it was ready and why it failed. The staff app's Analyst dashboard lists them. The platform sends no notifications, so this list is the only place a failure shows. How the runs happen is in reporting-service's [README](../services/reporting-service/README.md#overnight-reports-lmkt-45); the two times are C5 settings ([C5](C5-config.md)).

Unlike the other reports, it reads the service's own `report_runs` table ([023](../database/schema/023_report_runs.sql)), not `reporting_trades`. It is about reports, never about clients, so it holds no client data either.

**Parameters:** none.

**Response `200`:**

```json
{
  "success": true,
  "timeZone": "America/New_York",
  "data": [
    {
      "id": "8f2c0a4e-1111-4b6e-9c55-000000000001",
      "name": "Activity by stock",
      "runDate": "2026-10-14",
      "status": "READY",
      "scheduledFor": "2026-10-15T06:00:00Z",
      "readyBy": "2026-10-15T11:00:00Z",
      "readyAt": "2026-10-15T06:04:12Z",
      "failureReason": null
    },
    {
      "id": "1d7e5b20-2222-4c1f-8a6b-000000000002",
      "name": "Stub overnight report",
      "runDate": "2026-10-14",
      "status": "FAILED",
      "scheduledFor": "2026-10-15T06:00:00Z",
      "readyBy": "2026-10-15T11:00:00Z",
      "readyAt": null,
      "failureReason": "Source data missing for 2026-10-14"
    }
  ]
}
```

- One entry per report per night, for the last 30 nights (`RECENT_NIGHTS` in [OvernightReportsService](../services/reporting-service/src/main/java/com/lemarketjames/reports/overnight/OvernightReportsService.java)). The most recent `runDate` comes first, then by `name`. No runs gives `data: []`.
- `timeZone` is the IANA ID of the reports time zone (`lmj.reports.time-zone`); the staff app shows every time in it. `runDate` (`YYYY-MM-DD`) is the trading day the run covers, in that zone: the run on the night of the 15th covers the 14th.
- `status` is one of [RunStatus](../services/reporting-service/src/main/java/com/lemarketjames/reports/overnight/RunStatus.java): `SCHEDULED`, `RUNNING`, `READY`, `LATE` or `FAILED`.
- `scheduledFor`, `readyBy` and `readyAt` are ISO-8601 UTC instants with a `Z`. `readyBy` is when a successful run must be available by, the ready-by time of that night (07:00 by default, `lmj.reports.ready-by`), so the app hard-codes neither the time nor the zone.
- A run is `READY` when it finished by `readyBy`. It is `LATE` once `readyBy` has passed without it being ready: at that time a waiting or running run becomes `LATE` and carries on, and a run that finishes after `readyBy` is `LATE` too. A `LATE` run that then fails becomes `FAILED`.
- `readyAt` is when the run finished: set when `READY`, and when `LATE` once the late run has finished (a `LATE` run with no `readyAt` is still waiting or running). `failureReason` is set only when `FAILED`. Otherwise each is `null`, never left out.
- A run is `FAILED` when its report failed, with the report's message as the reason. It is also `FAILED` when reporting-service was down for the night's whole window (the reason starts "Did not run"), or stopped while the run was going (the reason starts "Interrupted"); so a night is never just missing from the list. `failureReason` is a message an analyst can read, never a stack trace.

**Failures:**

| HTTP | When | Body |
|---|---|---|
| 401 | Not authenticated | see access rules above |
| 403 | Not ANALYST role (`CLIENT` and `TRADING_OPS` included) | see access rules above |

## The consumers (notification-, surveillance- and activity-service)

Three services read the [order events](#the-order-events) from Kafka and serve what they make of them. Each listens in a consumer group of its own, with one listener per topic it reads, and keeps its results in a table of its own ([018](../database/schema/018_event_consumers.sql)). notification- and activity-service read one topic each; surveillance-service reads two.

| Service | Event it reads | Listener (topic and consumer group) | What it does with an event | Read back with |
|---|---|---|---|---|
| notification-service (`:8087`) | `OrderStatusChanged` | [OrderStatusListener](../services/notification-service/src/main/java/com/lemarketjames/notifications/OrderStatusListener.java) | Stores one notification per status change, for the order's client | [`GET /api/v1/notifications`](#get-apiv1notifications-client-only), trading gateway |
| surveillance-service (`:8088`) | `OrderSubmitted` | [OrderSubmittedListener](../services/surveillance-service/src/main/java/com/lemarketjames/surveillance/OrderSubmittedListener.java) | Raises an alert for Trading Ops when the order is large | [`GET /api/v1/surveillance/alerts`](#get-apiv1surveillancealerts-trading_ops-only), staff gateway only |
| | `OrderStatusChanged` | [OrderStatusChangedListener](../services/surveillance-service/src/main/java/com/lemarketjames/surveillance/OrderStatusChangedListener.java), in the same consumer group | Raises an alert for Trading Ops when the order's settlement was refused | the same endpoint |
| activity-service (`:8091`) | `OrderFilled` | [OrderFilledListener](../services/activity-service/src/main/java/com/lemarketjames/activity/OrderFilledListener.java) | Stores the fill, without its account | [`GET /api/v1/market-activity`](#get-apiv1market-activity), trading gateway |

Rules for all three, and for any consumer added later:

- **Its own shape of the event.** A consumer reads the record value with a record class of its own that names only the fields it uses, through [OrderEventReader](../libs/common/src/main/java/com/lemarketjames/common/events/OrderEventReader.java). It depends on none of buy-sell-service's classes, and statuses and sides stay text, so a field or a status added by the publisher breaks nobody.
- **The same event twice changes nothing.** Kafka delivers at least once: a record can arrive again after a restart or a failed commit. Each consumer checks for the result before storing it, and a unique key in its table guarantees it: one notification per order and status, one alert per order and reason ([019](../database/schema/019_order_alert_reasons.sql)), one recorded fill per order.
- **A record that can't be used is skipped.** A value that isn't valid JSON, or that lacks a field the consumer needs, is logged and skipped, so one bad record never blocks the ones behind it.
- **A new consumer group starts at the oldest record**, so events published before the service first started are not missed. From then on it continues from its group's committed position.
- **Nothing depends on a consumer.** Publishing is best effort (see [Outbound events](#internal-events-and-the-execution-interface-buy-sell-service)), so a notification, an alert or a recorded fill can be missing. Orders, holdings and the audit trail never read these tables; no service calls a consumer, and a consumer calls no service.
- **The switch.** A consumer listens only where `LMJ_EVENTS_CONSUMER=kafka` is set ([C5](C5-config.md#order-events)). Elsewhere it starts no listener and its endpoint answers with what its table already holds.

### GET /api/v1/notifications (CLIENT only)

The signed-in client's newest notifications (up to 50), newest first. There is no account in the path: the account is the caller's own.

```json
{
  "success": true,
  "notifications": [
    { "notificationId": 12, "orderId": 42, "status": "FILLED", "previousStatus": "PENDING",
      "message": "Order #42 is now FILLED (was PENDING)", "occurredAt": "2026-10-08T14:30:02Z" }
  ]
}
```

- One entry per status change of an order ([C1](C1-orders.md)); placing an order is not a status change, so the first entry of an order is it leaving `SUBMITTED`. `occurredAt` is when the order changed status, ISO-8601 UTC.
- No notifications is `200` with `notifications: []`. A login without an account gets the same `404 ACCOUNT_NOT_FOUND` as holdings-service gives.
- A missing or expired cookie gets `401`; staff roles get `403`.

### GET /api/v1/surveillance/alerts (TRADING_OPS only)

The newest alerts (up to 100), newest first by `occurredAt`. Served by the staff gateway only; the trading gateway has no route to it.

```json
{
  "success": true,
  "largeOrderQuantity": 100,
  "alerts": [
    { "alertId": 3, "orderId": 42, "accountId": 7, "instrumentId": 5, "side": "BUY", "quantity": 150,
      "price": 244.2366, "reason": "LARGE_ORDER", "submittedAt": "2026-10-08T14:30:00Z",
      "rejectionReason": null, "occurredAt": "2026-10-08T14:30:00Z" }
  ]
}
```

`reason` is an [AlertReason](../services/surveillance-service/src/main/java/com/lemarketjames/surveillance/AlertReason.java) name. An order has at most one alert per reason, so an order can appear more than once: a large order whose settlement is refused has both alerts.

| `reason` | Raised when | From | What the alert carries |
|---|---|---|---|
| `LARGE_ORDER` | An order is placed for at least the large-order quantity, a setting ([C5](C5-config.md)); `largeOrderQuantity` is its value in this environment, and the number above is an example. | `OrderSubmitted` | The order as it was placed: `instrumentId`, `side`, `quantity`, `price` (null for a SELL, which has no price until it fills) and `submittedAt`. `occurredAt` equals `submittedAt`; `rejectionReason` is null. |
| `SETTLEMENT_REFUSED` | An order is rejected because holdings-service refused its settlement ([Automatic execution and recovery](#automatic-execution-and-recovery)): the order moves to `REJECTED` with `INSUFFICIENT_CASH` (a BUY) or `INSUFFICIENT_HOLDINGS` (a SELL). | `OrderStatusChanged` | `rejectionReason`, which of the two it was, and `occurredAt`, when the order was rejected. `instrumentId`, `side`, `quantity`, `price` and `submittedAt` are null: the status change does not carry them. The order's [timeline](#order-timeline-trading_ops-only) has them. |

```json
{ "alertId": 4, "orderId": 42, "accountId": 7, "instrumentId": null, "side": null, "quantity": null,
  "price": null, "reason": "SETTLEMENT_REFUSED", "submittedAt": null,
  "rejectionReason": "INSUFFICIENT_CASH", "occurredAt": "2026-10-08T14:30:02Z" }
```

- An alert is not updated afterwards: a `LARGE_ORDER` alert shows the order as it was placed, whatever happens to it later.
- A rejection for any other reason raises no alert. The two codes are the ones a refused settlement gives, so a Trading Ops rejection made by hand with one of them ([Changing an order's status](#changing-an-orders-status-trading_ops-only)) is alerted to as well. An order refused at placement is never saved and publishes no event, so it raises none.
- A status change published before the event carried `rejectionReason` has none and raises no alert.
- `rejectionReason` and `occurredAt` were added after the other fields (LMKT-71).
- A missing or expired cookie gets `401`; every role but `TRADING_OPS` gets `403`.

### GET /api/v1/market-activity

How much of each stock traded in the last 24 hours, for anyone who is signed in. One entry per stock that traded, in instrument id order; a stock with no fill in the window has no entry.

```json
{
  "success": true,
  "windowHours": 24,
  "since": "2026-10-07T14:30:00Z",
  "activity": [
    { "instrumentId": 5, "trades": 2, "volume": 30, "turnover": 6210, "lastTradedAt": "2026-10-08T14:10:00Z" }
  ]
}
```

- `trades` counts fills, `volume` is shares (buys and sells together), `turnover` is the sum of quantity x price in USD. The window is a rolling 24 hours ending now ([MarketActivityService](../services/activity-service/src/main/java/com/lemarketjames/activity/MarketActivityService.java)), so it needs no time zone; `since` is its start.
- Aggregates only: the answer never names an order, an account or a client.
- A missing or expired cookie gets `401`. Every role may read it.

Portfolio history counts stocks only, like `invested`, so its last point is today's `invested`. There is no stored record of past holdings: the shares held at each point are rebuilt from the account's FILLED orders up to then (read from buy-sell-service like buying power), and valued at that point's price from market-service's history. Points before the first purchase are left out, so an account that has never owned a stock gets `points: []`. A failed orders or prices call also gives fewer points, never an error.

## Quotes (core-service, reading market-service)

| Method & path | Returns |
|---|---|
| `GET /api/quotes/{symbol}` | `{ success: true, quote }`, `404 { success: false, error: "Symbol not found" }`, or `503 { success: false, error: "Price unavailable" }` while the feed is down ([C4](C4-quote-feed.md)) |
| `GET /api/quotes` | `{ success: true, quotes: [quote…] }` sorted by symbol, or `503 { success: false, error: "Price unavailable" }` while the feed is down |

| `GET /api/quotes/{symbol}/history?range=1D` | `{ success: true, symbol, range, points: [{ time, price }] }`: the stock's prices over the [chart range](#chart-ranges), oldest first and ending with the live price; `400 { success: false, error: "Unknown range" }`, and the same `404` and `503` as a single quote |

`quote` = `{ symbol, name, price, priceChange, priceChangePercent, highPrice, lowPrice, openPrice, volume, marketCap, peRatio, dividendYield, lastUpdate }`.

### Chart ranges

The two history endpoints take the same `range` values, listed with the span each covers in [`ChartRange`](../libs/market-client/src/main/java/com/lemarketjames/market/model/ChartRange.java) (shared by core- and holdings-service, case-insensitive; the default is `1D`). `1D` is the current trading day from its 1-minute candles (`price_candles`), each point stamped at the end of its minute. Longer ranges have one point per trading day from the daily bars (`price_history`, [migration 022](../database/README.md)), counted back from today, each stamped at the exchange's closing time (`MarketHours.closingTime`, e.g. 16:00 New York = 20:00 UTC) so every `time` is an ISO-8601 UTC instant. Money is rounded to 2 decimals.

Feed-down detection is tracked by `MarketFeedStatus` (updated by every `MarketDataClient` call); the feed resumes automatically on the next successful call, with the outage's start and end logged ([C4](C4-quote-feed.md)).

## Sessions (core-service)

`POST /api/sessions/validate?accountId=…` with header `Authorization: Bearer <jwt>` returns `200 { success: true, session: { sessionId, accountId, expiresAt, isActive }, message }`, or `401 { success: false, message }` once the token has expired.

## Internal endpoints (service-to-service only, never through the gateway)

| Method & path | Caller → callee | Purpose |
|---|---|---|
| `GET /api/market/quotes`, `/quotes/{ticker}`, `/quotes/by-instrument/{id}` | core, buy-sell, holdings → market-service | Raw `QuoteSnapshot`s. Answers `503` while the feed is UNAVAILABLE ([C4](C4-quote-feed.md)). |
| `GET /api/market/history/{instrumentId}?from=YYYY-MM-DD`, `/intraday/{instrumentId}` | core, holdings → market-service | Past prices for the [chart ranges](#chart-ranges), through `PriceHistoryClient`: `DailyBar`s from `from` (inclusive, an exchange-local trading day), or today's `PricePoint`s; both end with the live quote. An unknown instrument has an empty list; `503` while the feed is UNAVAILABLE. |
| `POST /internal/holdings/settle` | buy-sell → holdings-service | Applies a fill's cash and holdings changes, and audits `SETTLED` |
| `POST /internal/holdings/validate` | buy-sell → holdings-service | SELL holdings check at placement |
| `/internal/market/control/**` | tests → market-service | Quote-feed test controls, only when enabled; see [C4](C4-quote-feed.md) |
| `/internal/holdings/control/settlement-faults/{accountId}` | tests → holdings-service | [The settlement test control](#the-settlement-test-control), only when enabled |

### The settlement test control

Makes the settlements of one account fail part-way, so a test can show on a running stack that a
failed settlement changes nothing and settles once when retried
([Automatic execution and recovery](#automatic-execution-and-recovery)). It exists only where its
setting is on ([C5](C5-config.md#settlement-test-control)), and is served by holdings-service
itself on port 8084.

```bash
curl -X PUT    localhost:8084/internal/holdings/control/settlement-faults/7   # account 7's settlements fail from now on
curl           localhost:8084/internal/holdings/control/settlement-faults/7   # { "accountId": 7, "failing": true, "failures": 2 }
curl -X DELETE localhost:8084/internal/holdings/control/settlement-faults/7   # they work again
```

- While an account is failing, `POST /internal/holdings/settle` for it answers `500` after the
  cash movement, the holding, the receipt and the `SETTLED` event have been written and before
  they are committed, so all of them are rolled back. buy-sell-service keeps the order `PENDING`
  with `settlement_pending` and retries on every poll.
- A settlement that is refused (`INSUFFICIENT_CASH`, `INSUFFICIENT_HOLDINGS`) is still answered
  as a refusal, and a request that already has a receipt still gets its original outcome.
- `failures` counts the settlements failed since the account was named; it is 0 once released.
- Other accounts are not affected, so tests can use it while the rest of a suite runs.

## Internal events and the execution interface (buy-sell-service)

- **Events.** buy-sell-service announces three things about an order: [OrderSubmitted](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderSubmitted.java) when it is placed, [OrderStatusChanged](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderStatusChanged.java) after every status transition, and [OrderFilled](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/events/OrderFilled.java) in addition when it fills. [The order events](#the-order-events) below says when each is published and what it carries. All three are in-process Spring events. Listen with `@EventListener`, or with `@TransactionalEventListener` to act only after the order's transaction commits.
- **Outbound events (Kafka).** After the order's transaction commits, [OrderEventForwarder](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventForwarder.java) hands each event to an [OrderEventPublisher](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventPublisher.java). With Kafka switched on, as in Docker Compose and Jenkins, [KafkaOrderEventPublisher](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/KafkaOrderEventPublisher.java) publishes them to the broker, where [three services consume them](#the-consumers-notification--surveillance--and-activity-service):
  - **Topics.** One per event. Their names are constants in [OrderEventTopicNames](../libs/common/src/main/java/com/lemarketjames/common/events/OrderEventTopicNames.java) in `libs/common`, where the publisher and the consumers both read them; [OrderEventTopics](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventTopics.java) creates them on the broker when buy-sell-service starts.
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

**`OrderStatusChanged`** tells a consumer where an order is in its life, and why when it was rejected.

| Field | Type | Meaning |
|---|---|---|
| `orderId` | number | The order |
| `accountId` | number | The order's account |
| `from` | text | Status before the transition ([C1](C1-orders.md)) |
| `to` | text | Status after it |
| `occurredAt` | time | When the transition happened |
| `rejectionReason` | text or null | Why the order was rejected: a [RejectionReason](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/entity/RejectionReason.java) name, the same code as the order's `rejectionReason` in the REST API. Null unless `to` is `REJECTED`. Added after the other fields (LMKT-71): a record published before then has no such field, and a consumer reads that as null. |

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

- **Audit (TRADING_OPS):** `GET /api/v1/audit?orderId=…|requestId=…|clientId=…&from=&to=` → `{ success, events: [{ eventType, orderId, accountId, clientId, requestId, occurredAt, details }] }`, oldest first, online events only ([C2](C2-audit.md), [C5](C5-config.md) retention). `requestId` is how a refused order's trail is found; `orderId` and `accountId` can be null on its events.
- **Reports (ANALYST):** the service, its access rule, `GET /api/v1/reports/ping`, `GET /api/v1/reports/trades`, the [volume insight](#get-apiv1reportsinsightsvolume) and the [overnight reports list](#get-apiv1reportsovernight) are built; see [Reports](#reports-reporting-service). The activity and instruments reports below are still planned. Their response shapes were agreed before the reports rules were finalized, so the story that builds each one must first reconcile it with the rules and update this section:
  - `GET /api/v1/reports/activity?startDate=&endDate=&limit=50&offset=0` → `{ success, activities: [{ id, type, symbol, quantity, price, totalAmount, timestamp }], total, limit, offset }`. `startDate` and `endDate` are days in the reports time zone, both included.
  - `GET /api/v1/reports/instruments?sortBy=gainLoss|gainLossPercent|quantity|value&order=ASC|DESC` → `{ success, instruments: [{ symbol, quantity, totalValue, gainLoss, gainLossPercent, performance: { week, month, threeMonth, year }, volatility, beta }] }`.
- **Candles:** `GET /api/v1/instruments/{symbol}/candles?limit=60` (max 390) → `{ success, symbol, interval: "1m", candles: [{ time, open, high, low, close, volume }] }` from `price_candles`, oldest first. An unknown symbol gets `404` in the quotes shape.
- **Watchlist (S41, LMKT-130/131/132):** core-service, in its own `watchlist` feature package (the trading gateway's catch-all already routes `/api/v1/watchlist` there). It needs a new `watchlist (account_id, instrument_id, added_at)` table, unique on `(account_id, instrument_id)`, in the next free migration number. Scoped to the caller's account from the JWT, like holdings; staff (no account) get the holdings-style `404 ACCOUNT_NOT_FOUND`.
  - `GET /api/v1/watchlist` → `{ success: true, watchlist: [{ symbol, addedAt }] }`, newest first; an empty list is `200` with `watchlist: []`.
  - `POST /api/v1/watchlist { symbol }` → `201 { success: true, item: { symbol, addedAt } }`; `409 { success: false, code: "ALREADY_WATCHED", error }`; `404 { success: false, error: "Symbol not found" }` for a symbol not in `GET /api/v1/instruments`.
  - `DELETE /api/v1/watchlist/{symbol}` → `204`, also when the stock was not on the list, so a repeated remove is harmless.
  - Prices are not part of the watchlist: the trading app shows each watched stock's live quote from `GET /api/quotes`, which it already polls.
  - Until the back end exists `GET /api/v1/watchlist` answers `404`, which the trading app reports as the watchlist not being available yet, without stars on the stock list.

## Changing a contract

- Endpoint paths and methods, request fields and response fields are fixed once published. Additive, backward-compatible changes are fine.
- Removing or renaming anything needs team agreement first.
- Change this file in the same PR as the code, so it never describes something the code doesn't do.
