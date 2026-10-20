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
2. reporting-service uses 1 database connection instead of 3, so reports can't take CPU from order processing.

Reports still answer within 4 s, inside BR-16's 5–10 s.

**Test:** [report-load.spec.ts](../../apps/e2e/load/report-load.spec.ts) (`npm run test:load`, Jenkins stage "Run report load test"). Measured on a local 4-CPU machine; still to be confirmed in Jenkins.
