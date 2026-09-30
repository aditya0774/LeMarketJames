# Code coverage

Generated at commit `7ef42f88` on 2026-09-30 by [build-coverage-site.mjs](../../scripts/coverage/build-coverage-site.mjs). Do not edit by hand.

Full clickable reports: https://aditya0774.github.io/LeMarketJames/coverage/

## Summary

| Area | Line % | Branch % | Lines missed / total |
|---|---:|---:|---:|
| [common](https://aditya0774.github.io/LeMarketJames/coverage/backend/common/index.html) | 43.51 | 27.27 | 174 / 308 |
| [market-client](https://aditya0774.github.io/LeMarketJames/coverage/backend/market-client/index.html) | 53.73 | 67.50 | 31 / 67 |
| [auth-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/auth-service/index.html) | 95.65 | 81.82 | 12 / 276 |
| [core-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/core-service/index.html) | 84.36 | 86.78 | 109 / 697 |
| [gateway-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/gateway-service/index.html) | 33.33 | 100.00 | 2 / 3 |
| [holdings-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/holdings-service/index.html) | 76.66 | 70.83 | 116 / 497 |
| [market-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/market-service/index.html) | 58.64 | 64.10 | 182 / 440 |
| **Back end (all modules)** | 72.64 | 73.09 | 626 / 2288 |
| **[Front end (Angular)](https://aditya0774.github.io/LeMarketJames/coverage/frontend/index.html)** | 87.94 | 76.93 | 159 / 1318 |
| **Everything** | 78.23 | 75.76 | 785 / 3606 |

_JaCoCo credits a module only for its own tests, so shared code in libs/ that the services’ tests exercise is under-reported._

## Lowest-covered back-end classes (most missed lines)

| Module | Class | Lines missed | Line % |
|---|---|---:|---:|
| common | `com.lemarketjames.common.domain.ClientEntity` | 56 | 0.00 |
| market-service | `com.lemarketjames.market.service.MarketPersistenceService` | 56 | 0.00 |
| common | `com.lemarketjames.common.domain.StaffUserEntity` | 34 | 0.00 |
| market-service | `com.lemarketjames.market.entity.InstrumentMarketParamsEntity` | 32 | 0.00 |
| market-service | `com.lemarketjames.market.entity.MarketQuoteEntity` | 31 | 6.06 |
| common | `com.lemarketjames.common.domain.AddressEntity` | 23 | 0.00 |
| market-client | `com.lemarketjames.market.client.MarketDataClient` | 22 | 0.00 |
| core-service | `com.lemarketjames.orders.entity.Order` | 20 | 71.01 |
| holdings-service | `com.lemarketjames.orders.client.OrdersClient` | 18 | 21.74 |
| market-service | `com.lemarketjames.market.entity.PriceCandleEntity` | 18 | 0.00 |

## Lowest-covered front-end files (most missed lines)

| File | Lines missed | Line % |
|---|---:|---:|
| `src/app/features/auth/register/register.ts` | 56 | 34.12 |
| `src/app/features/auth/register/register.html` | 21 | 91.18 |
| `src/app/features/dashboard/dashboard.ts` | 15 | 80.77 |
| `src/app/core/orders/orders.service.ts` | 12 | 7.69 |
| `src/app/features/orders/order-form/order-form.html` | 9 | 86.15 |
| `src/app/features/trade/trade-dialog.ts` | 9 | 90.82 |
| `src/app/core/orders/order.service.ts` | 6 | 78.57 |
| `src/app/features/trade/trade-dialog.html` | 6 | 92.59 |
| `src/app/core/holdings/holdings.service.ts` | 5 | 92.19 |
| `src/app/features/auth/login/login.html` | 5 | 86.11 |

## History (newest first)

| Date | Commit | Back-end line % | Back-end branch % | Front-end line % | Front-end branch % |
|---|---|---:|---:|---:|---:|
| 2026-09-30 | `7ef42f88` | 72.64 | 73.09 | 87.94 | 76.93 |
