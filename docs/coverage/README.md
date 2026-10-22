# Code coverage

Generated at commit `f76e293b` on 2026-10-22 by [build-coverage-site.mjs](../../scripts/coverage/build-coverage-site.mjs). Do not edit by hand.

Full clickable reports: https://aditya0774.github.io/LeMarketJames/coverage/

## Summary

| Area | Line % | Branch % | Lines missed / total |
|---|---:|---:|---:|
| [common](https://aditya0774.github.io/LeMarketJames/coverage/backend/common/index.html) | 58.79 | 78.75 | 211 / 512 |
| [market-client](https://aditya0774.github.io/LeMarketJames/coverage/backend/market-client/index.html) | 83.82 | 76.00 | 22 / 136 |
| [auth-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/auth-service/index.html) | 98.38 | 83.75 | 9 / 554 |
| [orders-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/orders-service/index.html) | 95.17 | 88.66 | 54 / 1118 |
| [staff-ops-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/staff-ops-service/index.html) | 83.66 | 86.74 | 133 / 814 |
| [trading-service](https://aditya0774.github.io/LeMarketJames/coverage/backend/trading-service/index.html) | 90.59 | 88.39 | 170 / 1807 |
| **Back end (all modules)** | 87.88 | 86.49 | 599 / 4941 |
| **[Front end (Angular)](https://aditya0774.github.io/LeMarketJames/coverage/frontend/index.html)** | 97.22 | 91.87 | 97 / 3494 |
| **Everything** | 91.75 | 90.03 | 696 / 8435 |

_JaCoCo credits a module only for its own tests, so shared code in libs/ that the services’ tests exercise is under-reported._

## Lowest-covered back-end classes (most missed lines)

| Module | Class | Lines missed | Line % |
|---|---|---:|---:|
| common | `com.lemarketjames.common.domain.ClientEntity` | 59 | 0.00 |
| common | `com.lemarketjames.common.domain.StaffUserEntity` | 37 | 0.00 |
| staff-ops-service | `com.lemarketjames.reports.overnight.ReportRunWriter` | 34 | 8.11 |
| trading-service | `com.lemarketjames.market.entity.InstrumentMarketParamsEntity` | 32 | 0.00 |
| common | `com.lemarketjames.common.instruments.Instrument` | 23 | 0.00 |
| common | `com.lemarketjames.common.domain.AddressEntity` | 23 | 0.00 |
| common | `com.lemarketjames.common.error.GlobalExceptionHandler` | 19 | 0.00 |
| trading-service | `com.lemarketjames.holdings.dto.HoldingDto` | 17 | 51.43 |
| orders-service | `com.lemarketjames.orders.entity.Order` | 16 | 80.00 |
| staff-ops-service | `com.lemarketjames.reports.overnight.DailySummaryWriter` | 15 | 28.57 |

## Lowest-covered front-end files (most missed lines)

| File | Lines missed | Line % |
|---|---:|---:|
| `src/app/features/auth/register/register.ts` | 22 | 81.51 |
| `src/app/features/orders/order-form/order-form.html` | 9 | 86.15 |
| `src/app/core/orders/order.service.ts` | 7 | 87.04 |
| `src/app/features/holdings/by-stock-report/by-stock-detail.component.ts` | 7 | 87.50 |
| `projects/shared-ui/src/lib/session-timeout/idle-session.ts` | 5 | 94.05 |
| `src/app/core/holdings/holdings.service.ts` | 5 | 92.19 |
| `src/app/features/holdings/by-stock-report/by-stock-report.component.html` | 5 | 85.71 |
| `src/app/features/orders/order-form/order-form.ts` | 5 | 92.42 |
| `src/app/features/account/account-page.ts` | 4 | 91.49 |
| `src/app/core/orders/account-orders.ts` | 3 | 94.12 |

## History (newest first)

| Date | Commit | Back-end line % | Back-end branch % | Front-end line % | Front-end branch % |
|---|---|---:|---:|---:|---:|
| 2026-10-22 | `f76e293b` | 87.88 | 86.49 | 97.22 | 91.87 |
| 2026-10-22 | `7f843769` | 87.33 | 85.75 | 97.22 | 91.87 |
| 2026-10-22 | `71853069` | 87.53 | 86.03 | 97.22 | 91.87 |
| 2026-10-21 | `a302a7d8` | 86.51 | 85.19 | 97.33 | 92.13 |
| 2026-10-20 | `da93a04c` | 86.51 | 85.19 | 97.33 | 92.13 |
| 2026-10-20 | `e4295008` | 86.31 | 85.08 | 97.34 | 92.15 |
| 2026-10-20 | `af9c7576` | 85.95 | 84.71 | 97.30 | 92.17 |
| 2026-10-20 | `716836b7` | 85.95 | 84.71 | 97.30 | 92.17 |
| 2026-10-20 | `fec3d368` | 85.75 | 84.57 | 97.28 | 92.15 |
| 2026-10-20 | `e538ed4c` | 85.75 | 84.57 | 96.93 | 91.46 |
| 2026-10-20 | `c38d7326` | 85.72 | 84.46 | 96.92 | 91.44 |
| 2026-10-20 | `45cc2bfd` | 85.65 | 84.46 | 96.92 | 91.44 |
| 2026-10-20 | `e8a41bb1` | 85.65 | 84.46 | 96.92 | 91.47 |
| 2026-10-20 | `e6338d05` | 85.65 | 84.46 | 96.92 | 91.47 |
| 2026-10-20 | `4f2b9890` | 85.77 | 84.99 | 96.81 | 91.47 |
| 2026-10-19 | `6476d511` | 85.40 | 85.01 | 96.92 | 91.47 |
| 2026-10-19 | `9b2a988c` | 85.13 | 84.84 | 96.92 | 91.47 |
| 2026-10-19 | `5521aa94` | 85.13 | 84.84 | 96.92 | 91.47 |
| 2026-10-19 | `d7719c5a` | 85.47 | 84.82 | 96.92 | 91.47 |
| 2026-10-19 | `4346f967` | 85.77 | 85.28 | 96.92 | 91.47 |
| 2026-10-19 | `50c887ba` | 84.41 | 82.43 | 90.97 | 82.92 |
| 2026-10-19 | `290eac1b` | 84.41 | 82.43 | 90.64 | 82.72 |
| 2026-10-16 | `91eea978` | 84.41 | 82.43 | 90.96 | 82.89 |
| 2026-10-16 | `67f24be7` | 84.41 | 82.43 | 90.96 | 82.89 |
| 2026-10-15 | `064bfce5` | 84.33 | 82.38 | 90.88 | 82.95 |
| 2026-10-15 | `facb2347` | 84.33 | 83.22 | 89.94 | 81.60 |
| 2026-10-15 | `2fb9fca7` | 84.33 | 83.22 | 90.28 | 81.77 |
| 2026-10-15 | `75dde5b0` | 84.33 | 83.22 | 90.28 | 81.77 |
| 2026-10-14 | `8d1f4741` | 84.33 | 83.22 | 89.80 | 81.31 |
| 2026-10-14 | `ff09122b` | 84.22 | 82.85 | 90.13 | 81.48 |
| 2026-10-14 | `7429338a` | 84.22 | 82.85 | 89.63 | 81.22 |
| 2026-10-14 | `33be27ee` | 84.06 | 81.75 | 89.61 | 81.12 |
| 2026-10-14 | `2e96c816` | 84.02 | 81.75 | 89.94 | 81.30 |
| 2026-10-13 | `d8ed2ed9` | 83.98 | 81.65 | 89.61 | 81.12 |
| 2026-10-13 | `8bd5f0cd` | 83.85 | 81.55 | 89.61 | 81.12 |
| 2026-10-13 | `a283b3ae` | 83.85 | 81.55 | 89.94 | 81.30 |
| 2026-10-13 | `f0449dd1` | 83.85 | 81.55 | 89.37 | 80.79 |
| 2026-10-13 | `76712563` | 83.85 | 81.55 | 89.37 | 80.79 |
| 2026-10-13 | `5982ea00` | 83.85 | 81.55 | 89.37 | 80.79 |
| 2026-10-13 | `bca5ed6c` | 84.10 | 82.05 | 89.37 | 80.79 |
| 2026-10-13 | `28555b2f` | 84.20 | 81.95 | 89.37 | 80.79 |
| 2026-10-12 | `3fd365b0` | 84.18 | 81.76 | 89.69 | 80.79 |
| 2026-10-12 | `3ec5c740` | 84.18 | 81.76 | 89.34 | 80.61 |
| 2026-10-12 | `7519faa2` | 84.00 | 81.60 | 89.69 | 80.79 |
| 2026-10-12 | `39e6a7ad` | 84.00 | 81.60 | 89.34 | 80.61 |
| 2026-10-12 | `345ecb5c` | 83.71 | 80.87 | 89.34 | 80.61 |
| 2026-10-12 | `b0f31fec` | 83.71 | 80.87 | 89.53 | 80.31 |
| 2026-10-12 | `19a048b9` | 83.71 | 80.87 | 89.18 | 80.13 |
| 2026-10-12 | `a50bead7` | 83.71 | 80.87 | 89.18 | 80.13 |
| 2026-10-12 | `182614a5` | 83.71 | 80.87 | 89.18 | 80.13 |
| 2026-10-09 | `0a811dc0` | 83.57 | 80.69 | 89.18 | 80.13 |
| 2026-10-09 | `92b54415` | 83.57 | 80.69 | 89.53 | 80.31 |
| 2026-10-08 | `7e204ecf` | 83.57 | 80.69 | 89.53 | 80.31 |
| 2026-10-08 | `b487e4a0` | 82.53 | 78.97 | 89.18 | 80.13 |
| 2026-10-08 | `c8a1e914` | 82.53 | 78.97 | 89.18 | 80.13 |
| 2026-10-07 | `1baab938` | 83.00 | 78.89 | 89.53 | 80.31 |
| 2026-10-07 | `c533c809` | 82.94 | 78.69 | 89.17 | 80.17 |
| 2026-10-07 | `08d6d8ee` | 82.94 | 78.69 | 89.52 | 80.35 |
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
