# staff-ops-service

The Analyst/Trading Ops service, on port 8086. It serves staff only:

- **Reports:** aggregate, read-only reports for analysts under `/api/v1/reports/**`. They are kept out of the trading services so that a heavy report can never slow down order placement.
- **Surveillance:** Trading Ops' alerts on large orders and refused settlements, `GET /api/v1/surveillance/alerts`, raised by a Kafka consumer from the order events ([C6](../../contracts/C6-api.md#the-consumers-notifications-surveillance-and-market-activity)).

The trading app has no route to it. The staff app (4201) is its only caller: staff sign in there and their browser's `staff_jwt` cookie reaches this service as the usual `jwt` ([C7](../../contracts/C7-roles.md#the-staff-session-cookie)). Port 8086 is published for debugging and tests only.

The two features share the service, not a connection pool. Reports read through a pool of their own, held to the read-side safeguards below ([ReportingDataSourceConfig](src/main/java/com/lemarketjames/config/ReportingDataSourceConfig.java)); surveillance writes its alerts, and the JWT filter reads the caller's login, through the ordinary pool ([PrimaryDataSourceConfig](src/main/java/com/lemarketjames/config/PrimaryDataSourceConfig.java)). A repository under `com.lemarketjames.reports` gets the reports' pool without asking; report code that reads through JDBC asks for `ReportingDataSourceConfig.JDBC`.

## Rules for every report endpoint

Read these before adding a report. They apply to all of them.

1. **Path and access.** Every report lives under `/api/v1/reports/`. [SecurityConfig](src/main/java/com/lemarketjames/config/SecurityConfig.java) lets only `ANALYST` in ([C7](../../contracts/C7-roles.md)) and denies every path outside that prefix and the surveillance one, so a new report needs no security change. It must also live in `com.lemarketjames.reports`, so it reads through the reports' pool.
2. **One data source.** A report reads only the `reporting_trades` view ([010](../../database/schema/010_shared_contracts.sql)). Never query `orders`, `accounts`, `clients` or any other table. There are two exceptions. The shared security filter in `libs/common` reads the caller's own `staff_users` row on every request, to refuse a deactivated login ([C7](../../contracts/C7-roles.md)). The [overnight reports](#overnight-reports-lmkt-45) list reads `report_runs`, which records report runs, not trades. A *trade* is a filled order, one row of that view. There are no fees: an amount is the view's `gross_amount` (quantity × price).
3. **Aggregates only.** A response holds totals, counts and averages over groups of trades, never a single trade and never anything that identifies an individual client. That rules out `client_id`, `account_id` and `order_id`, even though the view has them. Grouping by `segment`, `symbol`, side or time period is fine.
4. **Report time.** Days, weeks, months and years are those of the reports time zone, the `lmj.reports.time-zone` setting ([C5](../../contracts/C5-config.md)), while `filled_at` and `submitted_at` are stored in UTC. Take every date range from [ReportCalendar](src/main/java/com/lemarketjames/reports/period/ReportCalendar.java): it returns half-open UTC ranges (`filled_at >= start AND filled_at < end`). Don't convert time zones anywhere else. Weeks run Monday to Sunday.
5. **Read only.** A report never writes. The safeguards below enforce it, so a write fails instead of going unnoticed. The reports' one writer is the overnight scheduler's [ReportRunWriter](src/main/java/com/lemarketjames/reports/overnight/ReportRunWriter.java): it writes only `report_runs`, and only by making its own transactions read-write. Never copy that into a report.

## Read-side safeguards

Reports share the database with live trading. Three limits on the reports' own pool, set in [application.properties](src/main/resources/application.properties) as `reporting.datasource.hikari.*`, keep them from starving it:

| Safeguard | What it does | Override per environment |
|---|---|---|
| Small connection pool | Caps how many database connections reports hold, however many are requested at once | `REPORTING_DB_POOL_SIZE` |
| Read-only transactions | PostgreSQL refuses any write on the reports' connections | — |
| Query timeout | PostgreSQL cancels a query that runs too long, so it stops holding locks and CPU | `REPORTING_QUERY_TIMEOUT_MS` |

The last two are set on each connection when the pool opens it, so they hold for every query whether or not it runs in a Spring transaction. A report that hits the timeout fails; make the query cheaper rather than raising the limit. None of the three applies to the ordinary pool, which surveillance needs to write alerts.

That these limits are *enough* is shown by the report load test (LMKT-44, below). It times client orders until they fill, first alone and then while analysts run reports nonstop over a year of trades, and fails if reports make them noticeably slower. Run it after changing a limit or adding a report. Its thresholds and load levels are defined, with their overrides, at the top of [report-load.spec.ts](../../apps/e2e/load/report-load.spec.ts). If it fails, the agreed fallback is a Kafka-fed copy of fills owned by this service.

What the load test taught us, so a new report doesn't undo it:
- **Group in SQL.** Return one row per group, never the trades themselves. The trades report used to load every trade in its range and count in Java, which held the database long enough to slow order execution.
- **The pool is one connection on purpose.** Report queries are CPU-bound in PostgreSQL, so each busy connection takes a core from order execution. The reasoning and the measurements are next to the setting in [application.properties](src/main/resources/application.properties).

## Tests

| Test | Checks | Runs |
|---|---|---|
| [ReportAccessTest](src/test/java/com/lemarketjames/reports/ReportAccessTest.java) | No token gets `401`, every role but `ANALYST` gets `403`, `ANALYST` gets `200` | `mvn -B -pl services/staff-ops-service -am test` |
| [ReportCalendarTest](src/test/java/com/lemarketjames/reports/period/ReportCalendarTest.java) | Day, week, month and year ranges, day boundaries and daylight-saving changes | same |
| [VolumeInsightControllerTest](src/test/java/com/lemarketjames/reports/insight/VolumeInsightControllerTest.java) | The volume insight's access (`401`, every role but `ANALYST` gets `403`, including `CLIENT` and `TRADING_OPS`), response shape and `400`s | same |
| [VolumeInsightServiceTest](src/test/java/com/lemarketjames/reports/insight/VolumeInsightServiceTest.java) | Which two periods are compared for each period type, the change and percent (including no previous trades), time-zone and daylight-saving boundaries | same |
| [ActiveClientTrendControllerTest](src/test/java/com/lemarketjames/reports/insight/ActiveClientTrendControllerTest.java) | The active-client trend's access (`401`, every role but `ANALYST` gets `403`, including `CLIENT` and `TRADING_OPS`), response shape and that no identifier is returned | same |
| [ActiveClientTrendServiceTest](src/test/java/com/lemarketjames/reports/insight/ActiveClientTrendServiceTest.java) | The 30-day window ending today, zero-filled quiet days, local-day UTC ranges, daylight-saving and late-evening boundaries | same |
| [OvernightRunnerTest](src/test/java/com/lemarketjames/reports/overnight/OvernightRunnerTest.java) | A night's runs: every overnight report for the trading day before, finished with its time (READY or LATE is the writer's call), FAILED with its reason, one failure not stopping the next, no second run a night, LATE at the ready-by time, and the startup catch-up (run now, record as missed, or check last night) | same |
| [OvernightScheduleConfigTest](src/test/java/com/lemarketjames/reports/overnight/OvernightScheduleConfigTest.java) | Runs at 02:00 New York time, outside trading hours, and the deadline at 07:00; summer, winter and both daylight-saving nights; the configured times; refusing a run time not before the ready-by time | same |
| [OvernightReportsControllerTest](src/test/java/com/lemarketjames/reports/overnight/OvernightReportsControllerTest.java) | The overnight list's access (`401`, every role but `ANALYST` gets `403`) and its C6 shape, nulls included, with no client fields | same |
| [OvernightReportsServiceTest](src/test/java/com/lemarketjames/reports/overnight/OvernightReportsServiceTest.java) | The 30-night window in the reports time zone, the order, and `readyAt`/`failureReason` only where C6 allows them | same |
| [ReadSideSafeguardsIntegrationTest](src/test/java/com/lemarketjames/ReadSideSafeguardsIntegrationTest.java) | The three safeguards on the reports' pool, and that the ordinary pool has none of them, on a real PostgreSQL | Jenkins' integration stage; locally, the command in its Javadoc |
| [OvernightRunsIntegrationTest](src/test/java/com/lemarketjames/reports/overnight/OvernightRunsIntegrationTest.java) | On a real PostgreSQL: the run writer gets past the read-only default, a report runs once a night, READY by the ready-by time and LATE after it, a run only moves forward, a missed night is recorded once and an interrupted run is failed, the stub reads the real view, and every other write is still refused | same as the safeguards test |
| [StaffOpsServiceApplicationTest](src/test/java/com/lemarketjames/StaffOpsServiceApplicationTest.java) | Reports and surveillance get separate pools | same |
| [SurveillanceFlowTest](src/test/java/com/lemarketjames/surveillance/SurveillanceFlowTest.java) and the listener tests | Events become alerts, stored once, and only `TRADING_OPS` may read them | same |
| [staff-overnight-reports.spec.ts](../../apps/e2e/tests-staff/staff-overnight-reports.spec.ts) | Overnight reports through the real staff app and service: runs written into `report_runs` show as Ready, Late and Failed with their times and reason, Refresh picks up a change, and signed-out visitors and Trading Ops are refused | `cd apps/e2e && npm run test:staff`, with database access as in Jenkins (`E2E_ALLOW_DATABASE_SEED=true` and the `PG*` variables) |
| [reporting-access.spec.ts](../../apps/e2e/tests/reporting-access.spec.ts) | The same access rules on the running service, with the seed logins | `cd apps/e2e && npm test` |
| [report-load.spec.ts](../../apps/e2e/load/report-load.spec.ts) | Order confirmations stay on target while reports run (LMKT-44) | Jenkins' "Run report load test" stage; locally, `cd apps/e2e && npm run test:load` against a running stack |

The access tests load only the web layer. Tests that start the whole service use H2, set in `src/test/resources/application-default.properties`; that file applies only when no profile is active, so under `postgres-test` the service keeps its real configuration, read-side limits included. The PostgreSQL tests are skipped unless that profile is active.

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
staff login, Analyst dashboard, staff app and reporting API, with no mocked responses:

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
mvn -B -pl services/staff-ops-service -am "-Dspring.profiles.active=postgres-test" "-Dtest=SegmentActivityIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
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

## Overnight reports (LMKT-45)

Heavy reports run overnight so they are ready in the morning without slowing anything down
during the day. The code is in the `reports.overnight` package; the list it produces is
[`GET /api/v1/reports/overnight`](../../contracts/C6-api.md#get-apiv1reportsovernight), shown on
the staff app's Analyst dashboard.

| Piece | Does |
|---|---|
| `OvernightReportJob` | One overnight report. Every Spring bean of this type runs each night; a real report joins by adding a bean. Today the only one is `StubOvernightReportJob`. |
| `OvernightScheduleConfig` + `DailyTrigger` | Two triggers a night in the reports time zone, from C5: `lmj.reports.overnight-run-time` (02:00) starts the runs, `lmj.reports.ready-by` (07:00) is the deadline. The service refuses to start unless the run time is before the ready-by time. |
| `OvernightRunner` | A night's runs: schedules one run per report for the trading day before, then runs them one after another (the pool is small), and records each as READY, LATE or FAILED. At the deadline it marks LATE every run still waiting or running; those carry on. |
| `ReportRunWriter` | The reports' only writer, through their pool, to `report_runs` ([023](../../database/schema/023_report_runs.sql)) only (rule 5). |
| `OvernightReportsService` + controller | The list: the last 30 nights, most recent first. |

- **Once a night.** `UNIQUE (report_name, run_date)` means a restart or a second trigger never runs a report twice for the same night.
- **Failures.** There are no notifications, so a failure is shown only in the list. A report that throws is FAILED with its exception's message as the reason (its kind if it has none, never a stack trace; the stack trace goes to the log). 
- **Late.** A run not ready by the ready-by time is LATE, not FAILED: at that time a waiting or running run becomes LATE and carries on, and a run that finishes after it is LATE with the time it finished. A LATE run that then throws becomes FAILED. A run only moves forward, and a finished run never changes again.
- **The stub.** `StubOvernightReportJob` counts the run date's trades from `reporting_trades`, the way a real report reads (rules 2 to 4), and logs the count. The seed data already has trades on many days, so it needs no seeding of its own.
- **Daylight saving.** The triggers are not cron: cron skips a time the clocks jump over, and 02:00 does not exist in New York on the night clocks go forward. `DailyTrigger` takes times from `ReportCalendar.at`, so that night's run is at 03:00 instead of not at all, and the night clocks go back still has one run.
- **Startup catch-up.** So the list never just leaves a night out, `OvernightRunner.catchUp` runs once when the scheduler starts. It first fails, as interrupted, every run a previous process left unfinished. Then it looks at the most recent night: if its run time has passed but its ready-by time has not (a restart at 03:00), the missing reports run now; if the ready-by time has passed too (a start at 15:00), they are not run during trading hours but recorded as FAILED with "Did not run". Only the most recent night is checked. Both test profiles (`postgres-test` and the H2 default) turn the scheduler off, so tests never write runs.
- **Not covered yet.** Real heavy reports will need their own `SET LOCAL statement_timeout` above the 5-second read-side limit; decide the limit with the first one.

Two technical switches in [application.properties](src/main/resources/application.properties):

| Setting | Default | Use |
|---|---|---|
| `REPORTING_OVERNIGHT_ENABLED` | `true` | `false` turns the scheduler off on an instance that should only serve reports |
| `REPORTING_OVERNIGHT_STUB_FAILWITH` | blank | Makes the stub fail with this message, to show a failed run on a running stack |

To see a run without waiting for 2 a.m., start the service with `LMJ_REPORTS_OVERNIGHTRUNTIME` a
minute or two ahead (New York time, e.g. `14:05`) and `LMJ_REPORTS_READYBY` after it. Open
Overnight reports in the staff app as an analyst and use Refresh. A report runs once a night, so
to run it again the same day, delete its row as the database owner.

## Surveillance alerts

The consumer reads `lemarket.orders.submitted` and `lemarket.orders.status-changed` in the
`surveillance-service` consumer group (named after the service it was before the merge, so the
group's committed position carried over) and stores at most one alert per order and reason in
`order_alerts` ([018](../../database/schema/018_event_consumers.sql)). It listens only when
`LMJ_EVENTS_CONSUMER=kafka` ([C5](../../contracts/C5-config.md#order-events)). What counts as a large order is
`lmj.surveillance.large-order-quantity`; the alert reasons are
[AlertReason](src/main/java/com/lemarketjames/surveillance/AlertReason.java). The endpoint and its
response are in [C6](../../contracts/C6-api.md#get-apiv1surveillancealerts-trading_ops-only).
