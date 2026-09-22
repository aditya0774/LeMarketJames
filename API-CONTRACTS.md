# API Contracts

## Overview

This document defines the API contracts for LeMarketJames. All endpoints listed below represent the agreed-upon interface between frontend and backend development teams.

---

## Authentication Endpoints

### POST /api/auth/login

Authenticates a user and returns authentication tokens.

#### Request

```json
{
  "email": "user@example.com",
  "password": "securepassword123"
}
```

#### Response (200 OK)

```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "user": {
    "id": "user123",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

#### Response (401 Unauthorized)

```json
{
  "success": false,
  "error": "Invalid credentials"
}
```

---

## Orders Endpoints

### POST /api/v1/sell-orders

Submits a sell order via the dedicated sell-order service contract.

#### Request

```json
{
  "accountId": 1,
  "instrumentId": 101,
  "quantity": 5.0000
}
```

#### Response (201 Created)

```json
{
  "success": true,
  "orderId": 124,
  "accountId": 1,
  "instrumentId": 101,
  "orderType": "SELL",
  "quantity": 5.0000,
  "orderStatus": "SUBMITTED"
}
```

#### Response (400 Bad Request)

```json
{
  "success": false,
  "error": "Insufficient holdings. Available: 2.0000, Requested: 5.0000",
  "code": "INSUFFICIENT_HOLDINGS"
}
```

#### Response (400 Validation Error)

```json
{
  "errors": {
    "accountId": "accountId must be positive",
    "quantity": "quantity must be positive"
  }
}
```

#### Response (403 Forbidden)

```json
{
  "success": false,
  "error": "Access denied",
  "code": "ACCOUNT_ACCESS_DENIED"
}
```

#### Notes

- `orderType` is intentionally not accepted in this request and is always persisted as `SELL`.
- Holdings checks are enforced server-side through authenticated account ownership + available quantity validation.
- Frontend integration: `OrderService.createOrder(...)` routes validated SELL requests to `POST /api/v1/sell-orders`.

### POST /api/v1/buy-orders

Submits a buy order via the dedicated buy-order service contract.

#### Request

```json
{
  "accountId": 1,
  "instrumentId": 101,
  "quantity": 10.0000,
  "pricePerUnit": 150.25
}
```

#### Response (201 Created)

```json
{
  "success": true,
  "orderId": 123,
  "accountId": 1,
  "instrumentId": 101,
  "orderType": "BUY",
  "quantity": 10.0000,
  "pricePerUnit": 150.25,
  "orderStatus": "SUBMITTED"
}
```

#### Response (400 Bad Request)

```json
{
  "success": false,
  "reason": "Insufficient balance. Required: $1502.50, Available: $500.00"
}
```

#### Response (400 Validation Error)

```json
{
  "errors": {
    "accountId": "accountId must be positive",
    "pricePerUnit": "pricePerUnit must be positive"
  }
}
```

#### Response (403 Forbidden)

```json
{
  "success": false,
  "error": "Access denied",
  "code": "ACCOUNT_ACCESS_DENIED"
}
```

#### Notes

- `orderType` is intentionally not accepted in this request and is always persisted as `BUY`.
- Business validation for available cash is enforced server-side.
- Frontend integration: BUY submissions from the trade popup use `TradeDialog -> OrderService.submitBuyOrder(...)`, which calls `POST /api/v1/buy-orders`.

---

### POST /api/v1/orders

Creates a new order.

#### Request

```json
{
  "symbol": "AAPL",
  "quantity": 10,
  "type": "BUY",
  "orderType": "MARKET",
  "price": 150.25
}
```

#### Response (201 Created)

```json
{
  "success": true,
  "orderId": "order_12345",
  "symbol": "AAPL",
  "quantity": 10,
  "type": "BUY",
  "orderType": "MARKET",
  "price": 150.25,
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "executedAt": null
}
```

#### Response (400 Bad Request)

```json
{
  "success": false,
  "error": "Instrument is currently not tradable",
  "code": "NOT_TRADABLE"
}
```

#### Response (403 Forbidden)

```json
{
  "success": false,
  "error": "Access denied",
  "code": "ACCOUNT_ACCESS_DENIED"
}
```

#### Notes

- Tradability is enforced inline during order creation.
- Requests for instruments with `tradable=false` are rejected with the stable error code `NOT_TRADABLE`.
- Order creation is allowed only for the authenticated user's own account.
- Cross-account order attempts are rejected with HTTP 403 and error code `ACCOUNT_ACCESS_DENIED`.

---

### GET /api/orders/{id}

Retrieves a specific order by ID.

#### Path Parameters

- `id` (string, required): The order ID

#### Response (200 OK)

```json
{
  "success": true,
  "order": {
    "orderId": "order_12345",
    "symbol": "AAPL",
    "quantity": 10,
    "type": "BUY",
    "orderType": "MARKET",
    "price": 150.25,
    "status": "EXECUTED",
    "createdAt": "2024-01-15T10:30:00Z",
    "executedAt": "2024-01-15T10:30:45Z"
  }
}
```

#### Response (404 Not Found)

```json
{
  "success": false,
  "error": "Order not found"
}
```

---

### GET /api/orders

Retrieves all orders for the authenticated user.

#### Query Parameters

- `status` (string, optional): Filter by status (PENDING, EXECUTED, CANCELLED)
- `limit` (integer, optional): Number of results per page (default: 20)
- `offset` (integer, optional): Pagination offset (default: 0)

#### Response (200 OK)

```json
{
  "success": true,
  "orders": [
    {
      "orderId": "order_12345",
      "symbol": "AAPL",
      "quantity": 10,
      "type": "BUY",
      "orderType": "MARKET",
      "price": 150.25,
      "status": "EXECUTED",
      "createdAt": "2024-01-15T10:30:00Z",
      "executedAt": "2024-01-15T10:30:45Z"
    },
    {
      "orderId": "order_12346",
      "symbol": "MSFT",
      "quantity": 5,
      "type": "SELL",
      "orderType": "LIMIT",
      "price": 380.50,
      "status": "PENDING",
      "createdAt": "2024-01-15T11:00:00Z",
      "executedAt": null
    }
  ],
  "total": 42,
  "limit": 20,
  "offset": 0
}
```

---

## Holdings Endpoints

### GET /api/holdings

Retrieves all stock holdings for the authenticated user.

#### Response (200 OK)

```json
{
  "success": true,
  "holdings": [
    {
      "symbol": "AAPL",
      "quantity": 25,
      "averageCost": 145.50,
      "currentPrice": 150.25,
      "totalCost": 3637.50,
      "currentValue": 3756.25,
      "gainLoss": 118.75,
      "gainLossPercent": 3.27
    },
    {
      "symbol": "MSFT",
      "quantity": 10,
      "averageCost": 375.00,
      "currentPrice": 380.50,
      "totalCost": 3750.00,
      "currentValue": 3805.00,
      "gainLoss": 55.00,
      "gainLossPercent": 1.47
    }
  ]
}
```

---

## Balance Endpoints

### GET /api/balance

Retrieves the account balance and portfolio summary.

#### Response (200 OK)

```json
{
  "success": true,
  "balance": {
    "cash": 5000.00,
    "invested": 7561.25,
    "totalValue": 12561.25,
    "buyingPower": 15000.00,
    "dayGainLoss": 173.75,
    "dayGainLossPercent": 1.38,
    "totalGainLoss": 300.00,
    "totalGainLossPercent": 2.45,
    "currency": "USD"
  }
}
```

---

## Quotes Endpoints

### GET /api/quotes/{symbol}

Retrieves the current stock quote for a given symbol.

#### Path Parameters

- `symbol` (string, required): The stock ticker symbol (e.g., AAPL, MSFT)

#### Response (200 OK)

```json
{
  "success": true,
  "quote": {
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "price": 150.25,
    "priceChange": 2.45,
    "priceChangePercent": 1.66,
    "highPrice": 151.50,
    "lowPrice": 148.75,
    "openPrice": 148.00,
    "volume": 52345600,
    "marketCap": 2350000000000,
    "peRatio": 28.5,
    "dividendYield": 0.42,
    "lastUpdate": "2024-01-15T16:00:00Z"
  }
}
```

#### Response (404 Not Found)

```json
{
  "success": false,
  "error": "Symbol not found"
}
```

---

## Reports Endpoints

### GET /api/reports/activity

Retrieves transaction activity and history.

#### Query Parameters

- `startDate` (string, optional): ISO 8601 format (e.g., 2024-01-01)
- `endDate` (string, optional): ISO 8601 format (e.g., 2024-01-31)
- `limit` (integer, optional): Number of results (default: 50)
- `offset` (integer, optional): Pagination offset (default: 0)

#### Response (200 OK)

```json
{
  "success": true,
  "activities": [
    {
      "id": "activity_001",
      "type": "BUY",
      "symbol": "AAPL",
      "quantity": 10,
      "price": 150.00,
      "totalAmount": 1500.00,
      "fee": 2.50,
      "timestamp": "2024-01-15T10:30:00Z"
    },
    {
      "id": "activity_002",
      "type": "DIVIDEND",
      "symbol": "AAPL",
      "amount": 12.50,
      "timestamp": "2024-01-10T09:00:00Z"
    }
  ],
  "total": 127,
  "limit": 50,
  "offset": 0
}
```

---

### GET /api/reports/instruments

Retrieves instrument performance and statistics.

#### Query Parameters

- `sortBy` (string, optional): Sort field (gainLoss, gainLossPercent, quantity, value)
- `order` (string, optional): Sort order (ASC, DESC)

#### Response (200 OK)

```json
{
  "success": true,
  "instruments": [
    {
      "symbol": "AAPL",
      "quantity": 25,
      "totalValue": 3756.25,
      "gainLoss": 118.75,
      "gainLossPercent": 3.27,
      "performance": {
        "week": 2.15,
        "month": 5.32,
        "threeMonth": 8.45,
        "year": 25.60
      },
      "volatility": 0.22,
      "beta": 1.2
    },
    {
      "symbol": "MSFT",
      "quantity": 10,
      "totalValue": 3805.00,
      "gainLoss": 55.00,
      "gainLossPercent": 1.47,
      "performance": {
        "week": 1.05,
        "month": 3.20,
        "threeMonth": 6.15,
        "year": 18.75
      },
      "volatility": 0.18,
      "beta": 0.95
    }
  ]
}
```

---

## Team Agreement

This API contract represents an agreement between frontend and backend development teams:

### Binding Agreements

✅ **Endpoint Names are Fixed**
- All endpoint paths and HTTP methods listed above are permanent and cannot be changed without formal team approval
- Breaking changes require discussion in team meetings and documentation

✅ **Request Bodies are Fixed**
- All JSON request payloads must follow the specified structure exactly
- Additional optional fields may be added with backward compatibility in mind
- No fields specified here should be removed without team approval

✅ **Response Bodies are Fixed**
- All JSON response payloads must include the fields and structure shown above
- Response status codes must match the documented examples
- Additional fields may be added with proper versioning if needed

### Team Responsibilities

✅ **Frontend Developers**
- May mock against these contracts for parallel development
- Should follow the request/response structure exactly as documented
- Must test integration against live backend before production release

✅ **Backend Developers**
- Must implement endpoints matching these contracts precisely
- Should not add, remove, or modify response fields without approval
- Must maintain consistency with documented status codes and error responses

### Change Management

⚠️ **Changes Require Team Approval**
- Any modification to endpoint names, paths, or HTTP methods requires unanimous team decision
- Changes to request body fields require notification to frontend team
- Changes to response body structure require backend team discussion and frontend team approval
- Use semantic versioning for API versions if breaking changes are necessary
- All changes must be documented in a CHANGELOG.md file

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2024-01-15 | Team | Initial API contract definition |
| 1.1.0 | 2026-09-21 | Frontend | Added "Dashboard & Trade: backend endpoints needed" (balance, instruments, order symbol, candles, watchlist) |

---

*This document is subject to change only through formal team review and approval.*


## IA-08: Own-data access

- `POST /api/auth/login` returns `{ username, message, accountId }` and the HTTP-only JWT cookie.
- `GET /api/auth/me` returns `{ username, accountId }` resolved from the authenticated identity and persisted account.
- `GET /api/v1/holdings?accountId=...` and `POST /api/v1/holdings/validate` validate account ownership before repository access. `/api/holdings` remains a compatibility alias.
- Order detail, account lists, status-filtered lists, updates, and rejection validate ownership. Instrument order searches return only the caller's orders.
- Missing or foreign order IDs return the same HTTP 403 body: `{ "success": false, "error": "Access denied", "code": "ACCOUNT_ACCESS_DENIED" }`.
- Session validation requires the JWT subject to match the authenticated caller and the requested account to belong to that caller.
- Angular restores identity before initial navigation, clears cached holdings when accounts change, and does not submit SELL orders until holdings validation succeeds.

Existing clients/accounts/holdings tables already persist the ownership relationship; IA-08 needs no new columns or migration. See `docs/IA-08-TESTING.md` for local checks and EC2/Jenkins verification.

---

## Dashboard & Trade: backend endpoints needed

The Angular dashboard (`/dashboard`) and trade page (`/trade/:symbol`) are built from the LeUI mockup against the endpoints that exist today. The gaps below are shown as "—" / "Unavailable" in the UI or covered by a temporary frontend stand-in. Each entry lists what the frontend expects and which frontend file switches over once the endpoint ships.

**Rules for every endpoint in this section**

- Authenticated by the existing JWT cookie. Return `401` when it is missing or expired (the dashboard shows a "session expired" banner on 401).
- Scoped to the caller's own account, resolved from the JWT the same way as `GET /api/auth/me`. **None of them take an `accountId` parameter.**
- Ownership failures return the IA-08 body: `403 { "success": false, "error": "Access denied", "code": "ACCOUNT_ACCESS_DENIED" }`.
- Money is a JSON number (BigDecimal) in USD. Timestamps are ISO-8601 UTC strings (`2026-09-21T14:30:00Z`).
- Prices come from `MarketDataService`, never straight from `market_quotes`, so the dashboard, quotes, and holdings all agree (`market/service/MarketDataService.java`).

Priority order: 1 and 2 unblock the most UI, 3 is a small additive change, and 4 and 5 are nice-to-have.

### 1. GET /api/balance (contracted above, not implemented yet)

The contract already exists in **Balance Endpoints** above. These are the details the dashboard relies on:

| Field | Source / rule |
|---|---|
| `cash` | `accounts.cash_balance` for the caller's account (`CashValidationService.getCashBalance` already reads it) |
| `buyingPower` | `cash` minus the cost of the caller's open BUY orders (status `SUBMITTED`, `ACCEPTED`, `PENDING`, `DELAYED`). Equal to `cash` if the backend doesn't reserve cash. |
| `invested` | Σ holdings `quantity × current price` |
| `totalValue` | `cash + invested` |
| `dayGainLoss` / `dayGainLossPercent` | Σ holdings `quantity × (price − openPrice)`, using `QuoteSnapshot.openPrice` (today's first price); percent is relative to Σ `quantity × openPrice` |
| `totalGainLoss` / `totalGainLossPercent` | Same totals as `GET /api/v1/holdings` (`gainLoss`, relative to total cost) |

- Path: keep `/api/balance`, and add `/api/v1/balance` as an alias to match the `/api/v1/` convention.
- Errors: `401` when not authenticated; `404 { "success": false, "error": "Account not found" }` when the login has no account.
- **Frontend switch-over:** `features/dashboard/stat-strip/stat-strip.ts` (Buying power card, currently "—"; the "Total P/L" card becomes "Day P/L" as in the mockup) and `features/trade/trade.ts` (block BUY orders above `buyingPower`). `core/orders/orders.service.ts#getBalance` already calls this path.

### 2. GET /api/v1/instruments (new)

Lists instruments with a live price. It powers dashboard stock search and replaces the hard-coded list in the frontend.

#### Query Parameters

- `query` (string, optional): case-insensitive "contains" match on `ticker` **or** `name`. Empty or missing returns everything.
- `tradableOnly` (boolean, optional, default `false`): when `true`, only returns `instruments.tradable = TRUE`.

#### Response (200 OK)

```json
{
  "success": true,
  "instruments": [
    {
      "instrumentId": 5,
      "symbol": "TSLA",
      "name": "Tesla Inc",
      "assetClass": "EQUITY",
      "currency": "USD",
      "tradable": true,
      "price": 248.90,
      "priceChange": -5.80,
      "priceChangePercent": -2.28
    }
  ]
}
```

- Sort by `symbol` ascending. No pagination is needed at the current catalog size. Add `limit` later if the catalog grows.
- `price`, `priceChange`, and `priceChangePercent` use the same definitions as `GET /api/quotes/{symbol}`. They are `null` if the simulator has no snapshot for that instrument (for example, non-tradable instruments).
- No matches return `200` with `"instruments": []`, not 404.
- Data: the `instruments` table joined with `MarketDataService.findAll()`. Read-only; no migration needed.
- **Frontend switch-over:** `core/market/instrument-catalog.ts` (currently a hard-coded copy of the seed rows in `001`/`006`) and `features/dashboard/stock-search/stock-search.ts` (currently polls `GET /api/quotes/{symbol}` once per symbol).

### 3. Add `symbol` and `instrumentName` to order responses (additive)

Every response from `/api/v1/orders` (create, get by id, account lists, status lists) should include two extra fields in `OrderResponse`:

```json
{
  "orderId": 42,
  "accountId": 7,
  "instrumentId": 5,
  "symbol": "TSLA",
  "instrumentName": "Tesla Inc",
  "orderType": "BUY",
  "quantity": 10,
  "pricePerUnit": 248.90,
  "orderStatus": "FILLED",
  "rejectionReason": null,
  "submittedAt": "2026-09-21T14:30:00Z",
  "acceptedAt": "2026-09-21T14:30:00Z",
  "filledAt": "2026-09-21T14:30:01Z",
  "createdAt": "2026-09-21T14:30:00Z",
  "updatedAt": "2026-09-21T14:30:01Z"
}
```

- Non-breaking: existing fields stay unchanged. Fill the new fields from `instruments.ticker` / `instruments.name` via a join or `InstrumentRepository`.
- **Frontend switch-over:** `features/dashboard/orders-panel/orders-panel.ts` (`symbolFor` / `nameFor` currently resolve `instrumentId` through the frontend catalog).

### 4. GET /api/v1/instruments/{symbol}/candles (new, optional)

Price history for the trade page chart. Today the sparkline only shows prices polled since the page was opened.

#### Query Parameters

- `limit` (integer, optional, default `60`, max `390`): the most recent N one-minute candles.

#### Response (200 OK)

```json
{
  "success": true,
  "symbol": "TSLA",
  "interval": "1m",
  "candles": [
    { "time": "2026-09-21T14:29:00Z", "open": 248.10, "high": 249.00, "low": 247.95, "close": 248.90, "volume": 120400 }
  ]
}
```

- Data: the `price_candles` table (migration `006`), ordered by `interval_start` ascending.
- Errors: `404 { "success": false, "error": "Symbol not found" }`, the same shape as `GET /api/quotes/{symbol}`.
- **Frontend switch-over:** `features/trade/trade.ts` (seed `priceHistory` with the candle closes, then keep appending live quotes).

### 5. Watchlist (new, future)

The mockup has a Watchlist panel and star buttons in search results. They are deferred in the frontend until these endpoints exist.

**Migration** `database/schema/007_watchlist.sql`:

```sql
CREATE TABLE IF NOT EXISTS watchlist (
    account_id     INTEGER   NOT NULL REFERENCES accounts(account_id),
    instrument_id  INTEGER   NOT NULL REFERENCES instruments(instrument_id),
    added_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, instrument_id)
);
```

| Method & path | Request | Success | Errors |
|---|---|---|---|
| `GET /api/v1/watchlist` | — | `200 { "success": true, "items": [{ "instrumentId": 6, "symbol": "NVDA", "name": "NVIDIA Corp", "price": 189.62, "priceChange": 5.72, "priceChangePercent": 3.11, "addedAt": "…" }] }`, newest first | 401 |
| `POST /api/v1/watchlist` | `{ "symbol": "NVDA" }` | `201` with the created item (same shape as above) | 404 unknown symbol, 409 `{ "success": false, "error": "Already on watchlist", "code": "ALREADY_WATCHED" }` |
| `DELETE /api/v1/watchlist/{symbol}` | — | `204` | 404 if the symbol isn't on the caller's watchlist |

- Put this in its own `watchlist` feature package. It depends on Auth and reads prices through `MarketDataService` (keep the feature graph in `AGENTS.md` acyclic).

### Known mismatches to be aware of

- `apps/frontend/src/app/core/orders/orders.service.ts` calls `GET /api/orders` and `GET /api/balance`. Neither exists on the backend yet. The dashboard uses `core/orders/order.service.ts` (`/api/v1/orders/...`) instead.
- The **Orders Endpoints** contract above lists statuses `PENDING / EXECUTED / CANCELLED`, but the DB and backend use `SUBMITTED / ACCEPTED / PENDING / FILLED / REJECTED / DELAYED`. The dashboard follows the DB values, with filter chips All / Open (`SUBMITTED`, `ACCEPTED`, `PENDING`, `DELAYED`) / Filled / Rejected. Please update the contract text to the DB values rather than the other way round.
- The backend has no "order type" (MARKET/LIMIT) field. The trade page only offers Market orders; Limit is shown as "coming soon".
