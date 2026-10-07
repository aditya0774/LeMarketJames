# reporting-service

Aggregate, read-only reports for analysts, on port 8086 under `/api/v1/reports/**`. It is a separate service so that a heavy report can never slow down order placement.

It is not behind the trading gateway. The staff gateway (8090) is its only caller: staff sign in there and their browser's `staff_jwt` cookie reaches this service as the usual `jwt` ([C7](../../contracts/C7-roles.md#the-staff-session-cookie)). Port 8086 is published for debugging and tests only.

## Rules for every report endpoint

Read these before adding a report. They apply to all of them.

1. **Path and access.** Every report lives under `/api/v1/reports/`. [SecurityConfig](src/main/java/com/lemarketjames/config/SecurityConfig.java) lets only `ANALYST` in ([C7](../../contracts/C7-roles.md)) and denies every path outside that prefix, so a new report needs no security change.
2. **One data source.** A report reads only the `reporting_trades` view ([010](../../database/schema/010_shared_contracts.sql)). Never query `orders`, `accounts`, `clients` or any other table. A *trade* is a filled order, one row of that view. There are no fees: an amount is the view's `gross_amount` (quantity × price).
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

## Tests

| Test | Checks | Runs |
|---|---|---|
| [ReportAccessTest](src/test/java/com/lemarketjames/reports/ReportAccessTest.java) | No token gets `401`, every role but `ANALYST` gets `403`, `ANALYST` gets `200` | `mvn -B -pl services/reporting-service -am test` |
| [ReportCalendarTest](src/test/java/com/lemarketjames/reports/period/ReportCalendarTest.java) | Day, week, month and year ranges, day boundaries and daylight-saving changes | same |
| [ReadSideSafeguardsIntegrationTest](src/test/java/com/lemarketjames/ReadSideSafeguardsIntegrationTest.java) | The three safeguards, on a real PostgreSQL | Jenkins' integration stage; locally, the command in its Javadoc |
| [reporting-access.spec.ts](../../apps/e2e/tests/reporting-access.spec.ts) | The same access rules on the running service, with the seed logins | `cd apps/e2e && npm test` |

The Java tests need no H2 database: the access tests load only the web layer, and the safeguards test is skipped unless the `postgres-test` profile is active.
