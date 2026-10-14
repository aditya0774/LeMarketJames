# reporting-service

Aggregate, read-only reports for analysts, on port 8086 under `/api/v1/reports/**`. It is a separate service so that a heavy report can never slow down order placement.

It is not behind the trading gateway. The staff gateway (8090) is its only caller: staff sign in there and their browser's `staff_jwt` cookie reaches this service as the usual `jwt` ([C7](../../contracts/C7-roles.md#the-staff-session-cookie)). Port 8086 is published for debugging and tests only.

## Rules for every report endpoint

Read these before adding a report. They apply to all of them.

1. **Path and access.** Every report lives under `/api/v1/reports/`. [SecurityConfig](src/main/java/com/lemarketjames/config/SecurityConfig.java) lets only `ANALYST` in ([C7](../../contracts/C7-roles.md)) and denies every path outside that prefix, so a new report needs no security change.
2. **One data source.** A report reads only the `reporting_trades` view ([010](../../database/schema/010_shared_contracts.sql)). Never query `orders`, `accounts`, `clients` or any other table. The only exception is outside the reports: the shared security filter in `libs/common` reads the caller's own `staff_users` row on every request, to refuse a deactivated login ([C7](../../contracts/C7-roles.md)). A *trade* is a filled order, one row of that view. There are no fees: an amount is the view's `gross_amount` (quantity × price).
3. **Aggregates only.** A response holds totals, counts and averages over groups of trades, never a single trade and never anything that identifies an individual client. That rules out `client_id`, `account_id` and `order_id`, even though the view has them. Grouping by `segment`, `symbol`, side or time period is fine.
4. **Report time.** Days, weeks, months and years are those of the reports time zone, the `lmj.reports.time-zone` setting ([C5](../../contracts/C5-config.md)), while `filled_at` and `submitted_at` are stored in UTC. Take every date range from [ReportCalendar](src/main/java/com/lemarketjames/reports/period/ReportCalendar.java): it returns half-open UTC ranges (`filled_at >= start AND filled_at < end`). Don't convert time zones anywhere else. Weeks run Monday to Sunday.
5. **Read only.** A report never writes. The safeguards below enforce it, so a write fails instead of going unnoticed.

## Read-side safeguards

Reports share the database with live trading. Three limits, set in [application.properties](src/main/resources/application.properties), keep them from starving it:

| Safeguard | What it does | Override per environment |
|---|---|---|
| Small connection pool | Caps how many database connections reports hold, however many are requested at once | `REPORTING_DB_POOL_SIZE` |
| Read-only transactions | PostgreSQL refuses any write on this service's connections | — |
| Query timeout | PostgreSQL cancels a query that runs too long, so it stops holding locks and CPU | `REPORTING_QUERY_TIMEOUT_MS` |

The last two are set on each connection when the pool opens it, so they hold for every query whether or not it runs in a Spring transaction. A report that hits the timeout fails; make the query cheaper rather than raising the limit.

That these limits are *enough* is shown by the report load test (LMKT-44, below). It times client orders until they fill, first alone and then while analysts run reports nonstop over a year of trades, and fails if reports make them noticeably slower. Run it after changing a limit or adding a report. Its thresholds and load levels are defined, with their overrides, at the top of [report-load.spec.ts](../../apps/e2e/load/report-load.spec.ts). If it fails, the agreed fallback is a Kafka-fed copy of fills owned by this service.

What the load test taught us, so a new report doesn't undo it:
- **Group in SQL.** Return one row per group, never the trades themselves. The trades report used to load every trade in its range and count in Java, which held the database long enough to slow order execution.
- **The pool is one connection on purpose.** Report queries are CPU-bound in PostgreSQL, so each busy connection takes a core from order execution. The reasoning and the measurements are next to the setting in [application.properties](src/main/resources/application.properties).

## Tests

| Test | Checks | Runs |
|---|---|---|
| [ReportAccessTest](src/test/java/com/lemarketjames/reports/ReportAccessTest.java) | No token gets `401`, every role but `ANALYST` gets `403`, `ANALYST` gets `200` | `mvn -B -pl services/reporting-service -am test` |
| [ReportCalendarTest](src/test/java/com/lemarketjames/reports/period/ReportCalendarTest.java) | Day, week, month and year ranges, day boundaries and daylight-saving changes | same |
| [VolumeInsightControllerTest](src/test/java/com/lemarketjames/reports/insight/VolumeInsightControllerTest.java) | The volume insight's access (`401`, every role but `ANALYST` gets `403`, including `CLIENT` and `TRADING_OPS`), response shape and `400`s | same |
| [VolumeInsightServiceTest](src/test/java/com/lemarketjames/reports/insight/VolumeInsightServiceTest.java) | Which two periods are compared for each period type, the change and percent (including no previous trades), time-zone and daylight-saving boundaries | same |
| [ReadSideSafeguardsIntegrationTest](src/test/java/com/lemarketjames/ReadSideSafeguardsIntegrationTest.java) | The three safeguards, on a real PostgreSQL | Jenkins' integration stage; locally, the command in its Javadoc |
| [reporting-access.spec.ts](../../apps/e2e/tests/reporting-access.spec.ts) | The same access rules on the running service, with the seed logins | `cd apps/e2e && npm test` |
| [report-load.spec.ts](../../apps/e2e/load/report-load.spec.ts) | Order confirmations stay on target while reports run (LMKT-44) | Jenkins' "Run report load test" stage; locally, `cd apps/e2e && npm run test:load` against a running stack |

The Java tests need no H2 database: the access tests load only the web layer, and the safeguards test is skipped unless the `postgres-test` profile is active.

## Activity by client segment (LMKT-43)

`GET /api/v1/reports/activity` implements the [C6 segment report](../../contracts/C6-api.md#get-apiv1reportsactivity).
Its controller handles HTTP, its service obtains date boundaries from `ReportCalendar`, and its
repository performs one grouped SQL query over the view. Distinct client counting spans accounts;
only aggregate DTOs leave the service. The existing security and read-side limits apply.

`SegmentActivityControllerTest` and `SegmentActivityServiceTest` cover the response allowlist,
role refusals, date validation, defaults and daylight-saving boundaries in the usual unit run.
`SegmentActivityIntegrationTest` runs under `postgres-test`, including in Jenkins. It checks the
real view, exact aggregation on isolated session-local fixtures, and an under-ten-second baseline
for 100,000 synthetic trades across five segments and 1,000 clients. That baseline includes SQL
execution and JSON serialization, excludes startup and fixture setup, and does not simulate
concurrent traffic or the public view's underlying joins. No shared data is modified.

`apps/e2e/tests-staff/staff-by-segment-report.spec.ts` covers LMKT-116 through the real
staff login, Analyst dashboard, staff gateway and reporting API, with no mocked responses:

| LMKT-43 criterion | End-to-end check |
|---|---|
| AC1 | Two selected historical ranges: raw view records are independently summed and compared with the API and rendered table; an empty range shows the empty state. |
| AC2 | A typical historical report refresh and its rendered table complete within ten seconds, excluding login and date-entry setup. |
| AC3 | Only the aggregate response fields and table cells are exposed; the report offers no client drill-down or identifying fields. |
| AC4 | Signed-out users receive 401; clients and Trading Ops receive 403 even with valid sessions. The report page also denies access. |

Run against the seeded stack with `cd apps/e2e` then
`npm run test:staff -- staff-by-segment-report.spec.ts --workers=1`.
The totals check uses `tests/support/database.ts`: supply `E2E_DATABASE_URL`, or
`E2E_ALLOW_DATABASE_SEED=true` with the `PG*` connection variables, as in Jenkins.
This spec only reads data; historical windows exclude current executions, and segment names
come from C3's data. Jenkins already discovers it in the staff Playwright stage.

Run the PostgreSQL checks against the disposable test database using `TEST_DATABASE_*`:

```powershell
mvn -B -pl services/reporting-service -am "-Dspring.profiles.active=postgres-test" "-Dtest=SegmentActivityIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### Client-segment trends

The staff segment page uses `GET /api/v1/reports/activity/comparison` to compare
selected dates with a preceding equal-length period or a custom range. It displays
both periods and absolute/percentage changes for all three aggregate metrics,
including segments active in only one period. The service performs two bounded
aggregate reads, using ReportCalendar for both ranges, plus an all-time aggregate for
the lower summary and share chart. All-time distinct clients are counted across the
whole reporting view, independently of the selected dates; no client details leave it.
See C6 for zero-baseline and date semantics. Controller tests cover comparisons,
daylight-saving boundaries, custom dates, invalid ranges and role enforcement.
