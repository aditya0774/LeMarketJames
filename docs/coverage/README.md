# Code coverage

Generated at commit `61af0a91` on 2026-10-06 by [build-coverage-site.mjs](../../scripts/coverage/build-coverage-site.mjs). Do not edit by hand.

Full clickable reports: https://aditya0774.github.io/LeMarketJames/coverage/

## Summary

| Area | Line % | Branch % | Lines missed / total |
|---|---:|---:|---:|
| [common](https://aditya0774.github.io/LeMarketJames/coverage/backend/common/index.html) | 44.57 | 42.86 | 199 / 359 |
| [market-client](https://aditya0774.github.io/LeMarketJames/coverage/backend/market-client/index.html) | 76.34 | 72.73 | 22 / 93 |
| [auth-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/auth-service/index.html) | 95.65 | 81.82 | 12 / 276 |
| [buy-sell-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/buy-sell-service/index.html) | 93.65 | 85.77 | 54 / 851 |
| [core-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/core-service/index.html) | 83.19 | 75.00 | 38 / 226 |
| [gateway-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/gateway-service/index.html) | 81.82 | 100.00 | 2 / 11 |
| [holdings-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/holdings-service/index.html) | 84.32 | 77.14 | 82 / 523 |
| [market-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/market-service/index.html) | 82.70 | 70.00 | 77 / 445 |
| [reporting-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/reporting-service/index.html) | 96.36 | 100.00 | 2 / 55 |
| **Back end (all modules)** | 82.81 | 78.48 | 488 / 2839 |
| **[Front end (Angular)](https://aditya0774.github.io/LeMarketJames/coverage/frontend/index.html)** | 89.52 | 80.35 | 179 / 1708 |
| **Everything** | 85.33 | 79.73 | 667 / 4547 |

_JaCoCo credits a module only for its own tests, so shared code in libs/ that the services’ tests exercise is under-reported._

## Lowest-covered back-end classes (most missed lines)

| Module | Class | Lines missed | Line % |
|---|---|---:|---:|
| common | `com.lemarketjames.common.domain.ClientEntity` | 56 | 0.00 |
| common | `com.lemarketjames.common.domain.StaffUserEntity` | 34 | 0.00 |
| market-service | `com.lemarketjames.market.entity.InstrumentMarketParamsEntity` | 32 | 0.00 |
| common | `com.lemarketjames.common.instruments.Instrument` | 23 | 0.00 |
| common | `com.lemarketjames.common.domain.AddressEntity` | 23 | 0.00 |
| holdings-service | `com.lemarketjames.holdings.dto.HoldingDto` | 17 | 51.43 |
| buy-sell-service | `com.lemarketjames.orders.entity.Order` | 16 | 79.22 |
| core-service | `com.lemarketjames.sessions.entity.SessionEntity` | 15 | 0.00 |
| common | `com.lemarketjames.common.error.GlobalExceptionHandler` | 14 | 0.00 |
| common | `com.lemarketjames.common.domain.AccountEntity` | 13 | 23.53 |

## Lowest-covered front-end files (most missed lines)

| File | Lines missed | Line % |
|---|---:|---:|
| `src/app/features/auth/register/register.ts` | 56 | 34.12 |
| `src/app/features/dashboard/dashboard.ts` | 27 | 77.50 |
| `src/app/features/auth/register/register.html` | 21 | 91.18 |
| `src/app/core/orders/orders.service.ts` | 9 | 30.77 |
| `src/app/features/orders/order-form/order-form.html` | 9 | 86.15 |
| `src/app/features/trade/trade-dialog.ts` | 8 | 91.84 |
| `src/app/core/holdings/holdings.service.ts` | 5 | 92.19 |
| `src/app/features/auth/login/login.html` | 5 | 86.11 |
| `src/app/features/holdings/by-stock-report/by-stock-detail.component.ts` | 5 | 87.50 |
| `src/app/features/holdings/by-stock-report/by-stock-report.component.html` | 5 | 85.71 |

## History (newest first)

| Date | Commit | Back-end line % | Back-end branch % | Front-end line % | Front-end branch % |
|---|---|---:|---:|---:|---:|
| 2026-10-06 | `61af0a91` | 82.81 | 78.48 | 89.52 | 80.35 |
| 2026-10-06 | `6dedae6f` | 82.54 | 78.07 | 89.17 | 80.17 |
| 2026-10-06 | `ddd68400` | 82.54 | 78.07 | 89.17 | 80.17 |
| 2026-10-06 | `519930ed` | 82.43 | 78.03 | 89.17 | 80.17 |
| 2026-10-06 | `41f04049` | 82.43 | 78.03 | 89.17 | 80.17 |
| 2026-10-06 | `71769d7f` | 82.43 | 78.03 | 88.54 | 78.64 |
| 2026-10-06 | `1e8738e7` | 82.33 | 77.95 | 88.54 | 78.64 |
| 2026-10-05 | `d79f3a45` | 82.24 | 77.78 | 88.54 | 78.64 |
| 2026-10-05 | `4f7fc260` | 76.02 | 75.44 | 87.92 | 78.31 |
| 2026-10-05 | `cecbfde6` | 74.42 | 73.44 | 87.92 | 78.31 |
| 2026-10-02 | `90c9dbdf` | 74.11 | 72.19 | 88.37 | 78.55 |
| 2026-10-02 | `a3c9105d` | 74.11 | 72.19 | 87.92 | 78.29 |
| 2026-10-02 | `d6d184c2` | 74.11 | 72.19 | 87.92 | 78.29 |
| 2026-10-02 | `caa904ae` | 73.33 | 71.37 | 87.92 | 78.29 |
| 2026-10-01 | `79ec3ec8` | 72.83 | 69.80 | 87.92 | 78.29 |
| 2026-10-01 | `62b35882` | 72.69 | 69.80 | 87.92 | 78.29 |
| 2026-10-01 | `e803a8b0` | 72.69 | 69.80 | 87.74 | 76.90 |
| 2026-10-01 | `bd04e574` | 72.69 | 69.80 | 87.99 | 77.08 |
| 2026-10-01 | `d568e3af` | 72.64 | 73.09 | 88.38 | 77.18 |
| 2026-10-01 | `b3f24d2a` | 72.64 | 73.09 | 87.93 | 76.93 |
| 2026-09-30 | `7ef42f88` | 72.64 | 73.09 | 87.94 | 76.93 |
