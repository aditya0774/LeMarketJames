# LMKT-44: Report load performance

**Goal:** analysts running reports must not slow down client orders (BR-16).

**Result:** with reports running, order confirmation (p95) slowed by **+117 %** before the fixes and **+3 %** after them. That's within target, and the load test passed 4 runs out of 4.

| | Before | After fix 1 (SQL grouping) | After fix 2 (pool of 1) |
|---|---|---|---|
| Order p95, no reports | 1,549 ms | 1,599 ms | 1,339 ms |
| Order p95, reports running | **3,366 ms** | **2,335 ms** | **1,375 ms** |
| Slowdown caused by reports | +117 % | +46 % | **+3 %** |
| Orders done in 30 s (no reports → reports) | 96 → 57 | 100 → 66 | 108 → 100 |
| Reports answered in 30 s | 78 | 153 | 123 |
| Report time p50 / max | 3.7 s / 6.7 s | 1.4 s / 5.5 s | 2.2 s / 3.7 s |
| Load test | ❌ fail | ❌ fail | ✅ pass |

**Fixes**
1. The trades report groups trades in the database instead of loading every trade into the service.
2. The reports' connection pool (reporting-service then, staff-ops-service now) uses 1 database connection instead of 3, so reports can't take CPU from order processing.

Reports still answer within 4 s, inside BR-16's 5–10 s.

**Test:** [report-load.spec.ts](../../apps/e2e/load/report-load.spec.ts) (`npm run test:load`, Jenkins stage "Run report load test"). Measured on a local 4-CPU machine; still to be confirmed in Jenkins.

## BR-16: the nightly summary (separated read side)

The long-range reports (`/trades`, `/trades-by-stock`) now read the nightly trade summary for every
day it covers and the live view only after it ([README, "Read side"](README.md#read-side-br-16)).
Same load test, same machine (a MacBook, Apple silicon), run twice on 2026-10-21 on the same database:
first with the old code (every day live), then after the overnight job had built the summary.

| | Every day live | Summary + live tail |
|---|---|---|
| Order p95, no reports | 1,368 ms | 1,203 ms |
| Order p95, reports running | 1,300 ms | 1,252 ms |
| Slowdown caused by reports | −5 % (within noise) | +4 % (allowed: up to 1,453 ms) |
| Orders done in 30 s (no reports → reports) | 104 → 108 | 112 → 108 |
| Reports answered in 30 s (8 analysts) | 607 | 9,840 |
| Report time p50 / p95 / max | 414 ms / 633 ms / 827 ms | 22 ms / 58 ms / 242 ms |
| Load test | ✅ pass | ✅ pass |

Orders were already on target after LMKT-44's fixes; what the summary changes is how much database a
report costs: a year's report reads a few thousand summary rows plus today's trades instead of a
year of orders, so reports answer about 16 times as many requests in the same time, and the one
reports connection is busy for a fraction of it.

**Read with care:** the load test inserts its year of trade history (50,000 rows) just before it runs,
backdated, and removes it afterwards. A summary built earlier does not contain those rows, so with the
summary the reports skip them. In real use no trade is ever backdated (a fill is stamped when it
happens, and trades are final), so the next night's summary would hold them; the numbers above show
the cost of a summary read against a live scan, not two answers to the same data. The answers
themselves are checked separately: `OvernightRunsIntegrationTest` compares the summary with the live
view, and on the stack both reports matched direct SQL to the cent.
