# C5 – Configuration settings

Open product questions (how many login attempts, how stale a quote may be, ...) become configuration values instead of blockers. A setting's key, default and meaning are defined once, in its settings class. This page says where each setting lives and who uses it.

## Where settings live

| Settings | Defined in | Available to |
|---|---|---|
| Business settings: `lmj.*` | [PlatformSettings.java](../libs/common/src/main/java/com/lemarketjames/common/config/PlatformSettings.java) | auth-, orders-, trading- and staff-ops-service (anything on `libs/common`) |
| Market simulation: `sim.*` | [MarketSimulationProperties.java](../services/trading-service/src/main/java/com/lemarketjames/market/config/MarketSimulationProperties.java) | trading-service |
| Exchange trading hours | [MarketHours.java](../libs/market-client/src/main/java/com/lemarketjames/market/model/MarketHours.java) constants | trading-service and anything using market-client |
| Supported stock list | the `instruments` table (via migrations), served by `GET /api/v1/instruments` ([C6](C6-api.md#instruments-trading-service)) | everyone; the frontend never keeps a copy |

## Who uses each setting

| Setting (see the class for key and default) | Used by |
|---|---|
| Inactivity timeout: `PlatformSettings.Session` (`LMJ_SESSION_INACTIVITYTIMEOUT`, ISO-8601 such as `PT1M`) | auth-service (BR-03): a session token lasts only this long, so an idle session ends at every service on its own. `POST /api/auth/refresh` extends it while the user is active, never past `jwt.expiration-ms` from sign-in, which caps a session however busy ([C6](C6-api.md#auth-auth-service)). Both web apps (`@lemarket/shared-ui` `SessionTimeout`) warn 30 seconds before the end and then sign out to the login page. Docker Compose reads it from `SESSION_INACTIVITY_TIMEOUT` and `start-all.ps1` from `-SessionTimeout`, both `PT1M` by default; the e2e and load runs and Jenkins use `PT30M`, since their tests pause longer than a minute without input. |
| Lockout attempts and duration: `PlatformSettings.Auth.Lockout` | auth-service login, for clients and staff |
| Quote staleness limit: `PlatformSettings.Market` | orders-service, through `QuoteFreshness`: BUY placement refuses a stale quote, and execution rejects the order instead of filling at one ([C6](C6-api.md#automatic-execution-and-recovery)). |
| Market hours and holidays: `MarketHours`, `sim.respect-market-hours`, `sim.holidays` | trading-service's market simulator |
| Startup catch-up: `sim.catch-up-on-start` (`SIM_CATCH_UP_ON_START`) | trading-service's `MarketInitializer`: on startup, simulates the part of today's session missed while the service was down, so the day's charts start at the open ([C4](C4-quote-feed.md#market-hours)). |
| Location restriction list: `PlatformSettings.Orders` (`lmj.orders.restricted-locations`, empty by default) | orders-service, through `TradingRestrictions`: residence locations (US state names as the sign-up form stores them, or ISO country codes) that may trade **no** stock. Rules for single stocks are rows of `instrument_location_restrictions` instead ([028](../database/README.md#028--per-instrument-location-restrictions)), maintained by migration; 028 blocks the six non-US companies' stocks for New Jersey. Both are checked at placement and again at execution, and refuse with `LOCATION_RESTRICTED` ([C6](C6-api.md#post-apiv1orders)). |
| Audit online retention window: `PlatformSettings.Audit` (`lmj.audit.online-retention`, ISO-8601 such as `P365D`) | orders-service, through [AuditRetention](../services/orders-service/src/main/java/com/lemarketjames/audit/AuditRetention.java) (BR-14): an audit event older than the window counts as archived; its row is not changed, and nothing is deleted ([C2](C2-audit.md#retention-and-the-archive)). The order timeline and trade search mark archived events and trades, and `GET /api/v1/audit/archive` returns only archived events ([C6](C6-api.md#audit-archive-trading_ops-only)). Must be above zero; the service refuses to start otherwise. |
| Database backups: `BACKUP_TIME` (HH:MM, UTC) and `BACKUP_KEEP`, environment variables of the `db-backup` service in [docker-compose.yml](../docker-compose.yml); defaults in [backup-db-nightly.sh](../scripts/backup-db-nightly.sh) and [backup-db.sh](../scripts/backup-db.sh) | The nightly `pg_dump` (BR-14): when it runs, and how many copies are kept. `backup-db.sh` by hand reads `BACKUP_KEEP` too; `backup-db.ps1` takes `-Keep` ([database/README.md](../database/README.md#backups-and-restore)). |
| Reports time zone: `PlatformSettings.Reports` (`lmj.reports.time-zone`) | staff-ops-service, through [ReportCalendar](../services/staff-ops-service/src/main/java/com/lemarketjames/reports/period/ReportCalendar.java): the zone a report's days, weeks, months and years are in. Orders are stored in UTC; `ReportCalendar` converts ([C6](C6-api.md#reports-staff-ops-service)). It is also the zone the overnight report time is in. |
| Overnight report time: `PlatformSettings.Reports` (`lmj.reports.overnight-run-time`) | staff-ops-service's overnight scheduler: when each night's overnight reports start, in the reports time zone. Keep it outside trading hours ([C6](C6-api.md#get-apiv1reportsovernight)). |
| Overnight ready-by time: `PlatformSettings.Reports` (`lmj.reports.ready-by`) | staff-ops-service's overnight scheduler: when a successful run must be ready by, the same night. A run not finished by then is marked late, so the Analyst dashboard shows it; one that finishes after it stays late. Must be after the overnight report time; the service refuses to start otherwise. |
| Insight flag percent: `PlatformSettings.Reports` (`lmj.reports.insight-flag-percent`) | staff-ops-service, through [InsightFlagRule](../services/staff-ops-service/src/main/java/com/lemarketjames/reports/insight/InsightFlagRule.java) (BR-17): an Analyst insight that moves by at least this percent of the period it is compared with, up or down, is flagged; one that had none before and has some now always is. Every insight response carries the value in use as `flag.thresholdPercent` ([C6](C6-api.md#insight-flags)), so the staff app keeps no copy. Must be above zero; the service refuses to start otherwise. |
| Large-order quantity: `PlatformSettings.Surveillance` (`lmj.surveillance.large-order-quantity`) | staff-ops-service's surveillance: an order placed for at least this many shares raises an alert for Trading Ops ([C6](C6-api.md#get-apiv1surveillancealerts-trading_ops-only)). The alerts endpoint returns the value in use. |
| Internal API token: `internal.api-token` (`INTERNAL_API_TOKEN`), in trading- and orders-service's `application.properties` | Both services require it on their `/internal/**` endpoints, through `InternalApiTokenFilter`. orders-service sends it to trading-service when it settles a fill or checks a SELL. Both must have the same value: Docker Compose passes one variable to both, and `start-all.ps1` leaves both on the same development default. Set a real secret outside development ([C6](C6-api.md#internal-endpoints-service-to-service-only-never-through-a-web-app)). |
| Cash movement limit: `PlatformSettings.Cash` (`lmj.cash.movement-limit`, default `$50,000.00`) | trading-service: the largest amount accepted by one deposit or withdrawal ([C6](C6-api.md#deposit-and-withdraw-cash)). |

## Changing a value

- **Per environment:** set an environment variable; don't edit code or re-declare the key in `application.properties`. Spring maps `lmj.auth.lockout.max-attempts` to `LMJ_AUTH_LOCKOUT_MAXATTEMPTS`: dots become underscores and dashes are dropped. In Docker, add it under the service's `environment:` in `docker-compose.yml`.
- **The default itself:** change it in the settings class, where its Javadoc explains it. That is the only place the default is written.
- **A new setting:** add a field with a default and Javadoc to the settings class that owns it, and add a row here.

## Web apps: ports and service URLs

There is no gateway: each web app forwards `/api` to the services itself. Where the services
are is defined once per app and per way of running it, and nowhere else:

| | Trading app (4200) | Staff app (4201) |
|---|---|---|
| Docker (Nginx) | [nginx.conf](../apps/frontend/nginx.conf): `auth-service:8082`, `orders-service:8085`, `trading-service:8084` | [nginx.conf](../apps/frontend/projects/staff/nginx.conf): `auth-service:8082`, `orders-service:8085`, `staff-ops-service:8086` |
| `ng serve` (dev server) | [proxy.conf.json](../apps/frontend/proxy.conf.json): the same ports on `localhost`; [proxy.vm.json](../apps/frontend/proxy.vm.json) for the shared VM | [staff-proxy.mjs](../apps/frontend/projects/staff/staff-proxy.mjs), used by `proxy.conf.mjs` (`localhost`) and `proxy.vm.mjs` (the shared VM) |

- **Both web apps are HTTPS** in Docker (BR-02): each Nginx listens on 443 with a self-signed
  certificate made when its image is built, Compose publishes it as 4200 and 4201, and plain HTTP
  on those ports is redirected to HTTPS. `start-all.ps1` runs both dev servers with `--ssl`; a plain
  `ng serve` stays HTTP. See the README's [HTTPS and local-only ports](../README.md#https-and-local-only-ports).
- **The session cookie is Secure** wherever the apps are HTTPS: auth-service's `auth.cookie.secure`
  (`AUTH_COOKIE_SECURE`) is `true` in Docker Compose and `start-all.ps1`, and off by default for a
  plain-HTTP dev setup.
- Each service's own port is `server.port` in its `application.properties`: auth 8082, trading
  8084, orders 8085, staff-ops 8086. Docker Compose publishes each on the same host port, for
  direct debugging and the test controls; browsers never call them. Those ports, the database's and
  Kafka's are bound to `127.0.0.1` only (`SERVICE_BIND_ADDRESS`, BR-02), so another computer reaches
  only the web apps. A shared VM that others develop against sets `SERVICE_BIND_ADDRESS=0.0.0.0`.
- Services that call each other find each other through settings with `localhost` defaults,
  which Docker Compose sets to the container names ([docker-compose.yml](../docker-compose.yml)):
  orders-service's `MARKET_SERVICE_URL` and `HOLDINGS_SERVICE_URL` (both trading-service) and
  trading-service's `ORDERS_SERVICE_URL`. A call to `/internal/**` also carries the shared
  `INTERNAL_API_TOKEN` (see the internal API token row above).
- Both apps call `/api` on their own origin, so CORS only matters to a browser that calls a
  service's port directly. Services that accept browser POSTs allow `APP_CORS_ALLOWED_ORIGIN`,
  the trading app's origin: a comma-separated list, by default its `https://` and `http://`
  addresses on `localhost`, so both an HTTPS stack and a plain `ng serve` work.
- The staff app does not pass the browser's `Origin` header on to the services. They accept only
  the trading app's origin, and a browser sends `Origin` with every `POST`, staff sign-in
  included; dropping it means no service needs to know the staff app's origin.
- Which paths each app routes is in [C6](C6-api.md#which-web-app-routes-which-paths); the staff
  session cookie is in [C7](C7-roles.md#the-staff-session-cookie). A service that changes its port
  or Compose name changes in the files above with it.

## Reporting read-side limits

The reports' connection pool size, read-only transactions and query timeout are technical
limits, not business settings, so they are not in `PlatformSettings`. They apply to the reports'
own pool in staff-ops-service (`reporting.datasource.hikari.*`), not to the ordinary pool its
surveillance alerts are written through. They are defined, with their defaults and environment
overrides (`REPORTING_DB_POOL_SIZE`, `REPORTING_QUERY_TIMEOUT_MS`), in its
[application.properties](../services/staff-ops-service/src/main/resources/application.properties)
and explained in its [README](../services/staff-ops-service/README.md#read-side-safeguards).
The report load test ([report-load.spec.ts](../apps/e2e/load/report-load.spec.ts), LMKT-44)
checks that they are enough to keep order confirmations on target. Its thresholds are test
settings, defined with their `LOAD_*` overrides at the top of that file.

## Order execution

`lmj.execution` settings and their defaults live in
[ExecutionSettings.java](../services/orders-service/src/main/java/com/lemarketjames/orders/execution/ExecutionSettings.java):
`enabled` controls automatic polling, `poll-ms` its interval, `respect-market-hours` the exchange
session check, and `holidays` the exchange-local closed dates. The shared `MarketHours` model
defines sessions. Compose supplies the same `SIM_RESPECT_MARKET_HOURS` and `SIM_HOLIDAYS` inputs
to trading- and orders-service so simulation and execution agree. Tests can disable polling while
exercising the coordinator explicitly. Never disable polling in a live deployment that needs
automatic recovery of unfinished settlements.

## Settlement test control

trading-service can be told to make the settlements of one account fail part-way, so that
recovery can be tested on a running stack ([C6](C6-api.md#the-settlement-test-control)). The
control exists only where `lmj.settlement.control-enabled` is `true`; the key is defined on
[SettlementControlController.java](../services/trading-service/src/main/java/com/lemarketjames/holdings/SettlementControlController.java),
and without it there is nothing at the control's address. It is on in the Docker stack
(`LMJ_SETTLEMENT_CONTROL_ENABLED` in `docker-compose.yml`) and in `scripts/windows/start-all.ps1`,
and off otherwise. Never switch it on in a live deployment: whoever can reach trading-service's
port could stop an account's orders from settling.

## Order events

Whether orders-service publishes its order events to Kafka or only logs them
([C6](C6-api.md#internal-events-and-the-execution-interface-orders-service)) is one setting.
Its key, its two values and the default are defined in
[OrderEventPublisher.java](../services/orders-service/src/main/java/com/lemarketjames/orders/messaging/OrderEventPublisher.java):

| | Kafka | Stub (the default) |
|---|---|---|
| Set with | `LMJ_EVENTS_PUBLISHER=kafka` | nothing |
| Used in | Docker Compose and Jenkins ([docker-compose.yml](../docker-compose.yml)); the `kafka-test` profile of orders-service's tests | unit tests, the native Windows scripts, any stack with no broker |
| Needs a broker | yes, at `KAFKA_BOOTSTRAP_SERVERS` | no; none is contacted |

- The broker address and its default (`localhost:9092`) are in orders-service's
  [application.properties](../services/orders-service/src/main/resources/application.properties).
  Compose sets it to the container name and the broker's internal port, `kafka:29092`.
- The broker itself, its ports and its listeners are defined in
  [docker-compose.yml](../docker-compose.yml). Port 9092 is the one published to the host.
- The topic names are not settings: they are constants in
  [OrderEventTopicNames.java](../libs/common/src/main/java/com/lemarketjames/common/events/OrderEventTopicNames.java), shared by the publisher
  and the consumers.

The consuming side has the matching setting. Whether the notification, market-activity and
surveillance consumers (in trading- and staff-ops-service) listen to those events
([C6](C6-api.md#the-consumers-notifications-surveillance-and-market-activity)) is one setting
for all three, whose key and value are defined in
[OrderEventConsumerSwitch.java](../libs/common/src/main/java/com/lemarketjames/common/events/OrderEventConsumerSwitch.java):

| | Listening | Not listening (the default) |
|---|---|---|
| Set with | `LMJ_EVENTS_CONSUMER=kafka` | nothing |
| Used in | Docker Compose and Jenkins ([docker-compose.yml](../docker-compose.yml)); `start-all.ps1 -Kafka` | unit tests, the native Windows scripts, any stack with no broker |
| Needs a broker | yes, at `KAFKA_BOOTSTRAP_SERVERS` | no; none is contacted, and the endpoints answer with what is already stored |

- The broker address and its default are in the `application.properties` of trading- and
  staff-ops-service, next to the reason a new consumer group starts at the oldest record. Compose sets the address to
  `kafka:29092`, as for the publisher.
- A consumer's group name is a constant in its listener class. Each consumer has a group of its
  own, named after the service it was before the services were merged (`notification-service`,
  `activity-service`, `surveillance-service`), so the groups and their committed positions carried
  over.
