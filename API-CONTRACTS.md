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

### POST /api/orders

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
  "error": "Insufficient balance"
}
```

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

---

*This document is subject to change only through formal team review and approval.*
