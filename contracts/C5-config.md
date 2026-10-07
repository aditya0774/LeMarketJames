# C5 – Configuration settings

Open product questions (how many login attempts, how stale a quote may be, ...) become configuration values instead of blockers. A setting's key, default and meaning are defined once, in its settings class. This page says where each setting lives and who uses it.

## Where settings live

| Settings | Defined in | Available to |
|---|---|---|
| Business settings: `lmj.*` | [PlatformSettings.java](../libs/common/src/main/java/com/lemarketjames/common/config/PlatformSettings.java) | auth-, core-, holdings-, buy-sell- and reporting-service (anything on `libs/common`) |
| Market simulation: `sim.*` | [MarketSimulationProperties.java](../services/market-service/src/main/java/com/lemarketjames/market/config/MarketSimulationProperties.java) | market-service |
| Exchange trading hours | [MarketHours.java](../libs/market-client/src/main/java/com/lemarketjames/market/model/MarketHours.java) constants | market-service and anything using market-client |
| Supported stock list | the `instruments` table (via migrations), served by `GET /api/v1/instruments` ([C6](C6-api.md#instruments-core-service)) | everyone; the frontend never keeps a copy |

## Who uses each setting

| Setting (see the class for key and default) | Used by |
|---|---|
| Inactivity timeout: `PlatformSettings.Session` | Not yet. For the session-timeout story; today a session ends when its JWT expires. |
| Lockout attempts and duration: `PlatformSettings.Auth.Lockout` | auth-service login, for clients and staff |
| Quote staleness limit: `PlatformSettings.Market` | buy-sell-service, through `QuoteFreshness`: BUY placement refuses a stale quote, and execution rejects the order instead of filling at one ([C6](C6-api.md#automatic-execution-and-recovery)). |
| Market hours and holidays: `MarketHours`, `sim.respect-market-hours`, `sim.holidays` | market-service's simulator |
| Location restriction list: `PlatformSettings.Orders` | Not yet. For order placement, with the `LOCATION_RESTRICTED` code. |
| Audit online retention window: `PlatformSettings.Audit` | Not yet. For the audit archival and audit view stories. An event older than the window counts as archived; its row is not changed ([C2](C2-audit.md)). |
| Reports time zone: `PlatformSettings.Reports` (`lmj.reports.time-zone`) | reporting-service, through [ReportCalendar](../services/reporting-service/src/main/java/com/lemarketjames/reports/period/ReportCalendar.java): the zone a report's days, weeks, months and years are in. Orders are stored in UTC; `ReportCalendar` converts ([C6](C6-api.md#reports-reporting-service)). It is also the zone the overnight report time is in. |
| Overnight report time: `PlatformSettings.Reports` | Not yet. For the scheduled-reports story. |

## Changing a value

- **Per environment:** set an environment variable; don't edit code or re-declare the key in `application.properties`. Spring maps `lmj.auth.lockout.max-attempts` to `LMJ_AUTH_LOCKOUT_MAXATTEMPTS`: dots become underscores and dashes are dropped. In Docker, add it under the service's `environment:` in `docker-compose.yml`.
- **The default itself:** change it in the settings class, where its Javadoc explains it. That is the only place the default is written.
- **A new setting:** add a field with a default and Javadoc to the settings class that owns it, and add a row here.

## Gateways: ports and URLs

There are two backend entry points, both the `gateway-service` module. Where each one listens and
where it sends requests are technical settings, defined with their defaults in its configuration
file and nowhere else:

| | Trading gateway | Staff gateway |
|---|---|---|
| Started with | no profile | the `staff` profile (`SPRING_PROFILES_ACTIVE=staff`) |
| Defined in | [application.yml](../services/gateway-service/src/main/resources/application.yml) | [application-staff.yml](../services/gateway-service/src/main/resources/application-staff.yml), over `application.yml` |
| Port | `server.port` there; Docker Compose publishes it on host port 8089, and the native Windows scripts start it on 8089 too | `server.port` there (8090), the same in Docker and natively |
| Used by | the trading app (4200), through its dev proxy or Nginx | the staff app (4201), through its dev proxy or Nginx |
| Service locations | `AUTH_SERVICE_URL`, `BUY_SELL_SERVICE_URL`, `CORE_SERVICE_URL`, `MARKET_SERVICE_URL`, `HOLDINGS_SERVICE_URL` | `AUTH_SERVICE_URL`, `BUY_SELL_SERVICE_URL`, `REPORTING_SERVICE_URL` |
| Browser origin allowed to call it directly (CORS) | `APP_CORS_ALLOWED_ORIGIN` | `STAFF_CORS_ALLOWED_ORIGIN` |

- The defaults point at `localhost`, so a native stack needs none of these variables; Docker
  Compose sets them to the container names ([docker-compose.yml](../docker-compose.yml)).
- Both apps call `/api` on their own origin, so the CORS origins only matter to a browser that
  calls a gateway's port directly.
- The staff gateway does not pass the browser's `Origin` header on to the services. They accept
  only the trading app's origin (`APP_CORS_ALLOWED_ORIGIN`), and a browser sends `Origin` with
  every `POST`, staff sign-in included. `STAFF_CORS_ALLOWED_ORIGIN` is therefore the only place
  the staff app's origin is allowed; no service needs to know it.
- Which paths each gateway serves is in [C6](C6-api.md#which-gateway-serves-which-paths); the
  staff session cookie is in [C7](C7-roles.md#the-staff-session-cookie).
- The staff app's URLs are the two forwarding files next to it:
  [proxy.conf.json](../apps/frontend/projects/staff/proxy.conf.json) (dev server) and
  [nginx.conf](../apps/frontend/projects/staff/nginx.conf) (Docker). Change the staff gateway's
  port or Compose name and those two change with it.

## Reporting read-side limits

reporting-service's connection pool size, read-only transactions and query timeout are technical
limits, not business settings, so they are not in `PlatformSettings`. They are defined, with
their defaults and environment overrides, in its
[application.properties](../services/reporting-service/src/main/resources/application.properties)
and explained in its [README](../services/reporting-service/README.md#read-side-safeguards).

## Order execution

`lmj.execution` settings and their defaults live in
[ExecutionSettings.java](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/ExecutionSettings.java):
`enabled` controls automatic polling, `poll-ms` its interval, `respect-market-hours` the exchange
session check, and `holidays` the exchange-local closed dates. The shared `MarketHours` model
defines sessions. Compose supplies the same `SIM_RESPECT_MARKET_HOURS` and `SIM_HOLIDAYS` inputs
to market and buy-sell so simulation and execution agree. Tests can disable polling while
exercising the coordinator explicitly. Never disable polling in a live deployment that needs
automatic recovery of unfinished settlements.
