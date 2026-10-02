# C5 – Configuration settings

Open product questions (how many login attempts, how stale a quote may be, ...) become configuration values instead of blockers. A setting's key, default and meaning are defined once, in its settings class. This page says where each setting lives and who uses it.

## Where settings live

| Settings | Defined in | Available to |
|---|---|---|
| Business settings: `lmj.*` | [PlatformSettings.java](../libs/common/src/main/java/com/lemarketjames/common/config/PlatformSettings.java) | auth-, core- and holdings-service (anything on `libs/common`) |
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
| Audit online retention window: `PlatformSettings.Audit` | Not yet. For the audit archival and audit view stories ([C2](C2-audit.md)). |
| Overnight report time: `PlatformSettings.Reports` | Not yet. For the reporting story. |

## Changing a value

- **Per environment:** set an environment variable; don't edit code or re-declare the key in `application.properties`. Spring maps `lmj.auth.lockout.max-attempts` to `LMJ_AUTH_LOCKOUT_MAXATTEMPTS`: dots become underscores and dashes are dropped. In Docker, add it under the service's `environment:` in `docker-compose.yml`.
- **The default itself:** change it in the settings class, where its Javadoc explains it. That is the only place the default is written.
- **A new setting:** add a field with a default and Javadoc to the settings class that owns it, and add a row here.

## Order execution

`lmj.execution` settings and their defaults live in
[ExecutionSettings.java](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/execution/ExecutionSettings.java):
`enabled` controls automatic polling, `poll-ms` its interval, `respect-market-hours` the exchange
session check, and `holidays` the exchange-local closed dates. The shared `MarketHours` model
defines sessions. Compose supplies the same `SIM_RESPECT_MARKET_HOURS` and `SIM_HOLIDAYS` inputs
to market and buy-sell so simulation and execution agree. Tests can disable polling while
exercising the coordinator explicitly. Never disable polling in a live deployment that needs
automatic recovery of unfinished settlements.
