# AGENTS.md

**LeMarketJames:** A full-stack trading application with an Angular frontend, Spring Boot microservices, and one shared PostgreSQL database, kept together in a single monorepo. Conventions keep the codebase readable and development consistent.

## Architecture at a Glance

- **Trading app** (4200): `apps/frontend/src/app`, the Angular SPA for clients. Its Nginx (or the `ng serve` proxy, `apps/frontend/proxy.conf.json`) routes `/api` straight to the services: `/api/auth/**` to auth-service, `/api/v1/orders/**` and `/api/v1/buy-orders/**` to orders-service, everything else to trading-service.
- **Staff app** (4201): `apps/frontend/projects/staff/src/app`, a separate Angular SPA for staff (Trading Ops, Analyst). A second project in the same Angular workspace: same `package.json` and toolchain. The two share no features, routes or services; the UI building blocks both show the same way (the theme service and the account menu) live once in `apps/frontend/projects/shared-ui`, imported as `@lemarket/shared-ui`. Its Nginx (`projects/staff/nginx.conf`) or dev proxy (`projects/staff/staff-proxy.mjs`) forwards only the staff paths: sign-in, sign-out and `/api/auth/me` to auth-service, the Trading Ops trade search and order timeline to orders-service, `/api/v1/reports/**` and `/api/v1/surveillance/**` to staff-ops-service. Any other `/api` path answers 404. It gives staff their own session cookie, `staff_jwt`, by renaming the services' `jwt` in both directions ([C7](contracts/C7-roles.md#the-staff-session-cookie)).
- **There is no gateway.** Each app's Nginx or dev proxy does the routing; the services validate the cookie themselves.
- **Auth service** (8082): `services/auth-service`: registration, login, logout, `/api/auth/me`, for clients and staff.
- **Orders service** (8085): `services/orders-service`: order placement, lifecycle, automatic execution and history, plus Trading Ops' trade search and order timeline. Publishes the order events to Kafka. Calls trading-service for prices, sell validation and idempotent settlement.
- **Trading service** (8084): `services/trading-service`: the GBM price simulator (`GET /api/market/**`, open, read server-to-server) and its test controls (`/internal/market/control/**`, tests only), instruments, quotes, sessions, client profile, holdings, portfolio/balance, cash deposits and withdrawals, the client's watchlist (`/api/v1/watchlist`), trade history and buying power (reads orders back from orders-service). Settles fills via `POST /internal/holdings/settle`, never routed from a web app. Also hosts two Kafka consumers: a client's order notifications (`/api/v1/notifications`) and traded volume per stock (`/api/v1/market-activity`).
- **Staff ops service** (8086): `services/staff-ops-service`: aggregate, read-only reports for analysts under `/api/v1/reports/**`, built on the `reporting_trades` view, and Trading Ops' surveillance alerts (`/api/v1/surveillance/alerts`) raised by a Kafka consumer. Staff app only. Reports read through a pool of their own (one connection, read-only, time-limited); its [README](services/staff-ops-service/README.md) holds the rules every report endpoint follows.
- **Shared libraries**: `libs/common`: JWT (`JwtService`, `JwtAuthenticationFilter`), the internal token check for `/internal/**` (`InternalApiTokenFilter`), roles, shared account/client/instrument entities, the audit recorder, the order event topic names, `GlobalExceptionHandler`. `libs/market-client`: the `MarketDataService` interface, its quote/instrument model, and an HTTP implementation (`MarketDataClient`) that orders-service uses for prices; trading-service reads its simulator in-process.
- **Database** (5432): `database/schema`: one PostgreSQL database shared by every service, versioned via numbered SQL files.
- **Kafka** (9092): a single-node broker (KRaft, no ZooKeeper) defined in `docker-compose.yml`. orders-service publishes its order events to it in Docker and Jenkins; without a broker (unit tests, the native Windows scripts) it logs them instead ([C6](contracts/C6-api.md), setting in [C5](contracts/C5-config.md#order-events)). The consumers in trading-service and staff-ops-service read them from it, under the matching setting.
- **API:** All endpoints use `/api/v1/` prefix (auth is `/api/auth/`).

**Runtime flow:** web app (Nginx or dev proxy) → {`auth-service`, `orders-service`, `trading-service`, `staff-ops-service`} → `db`. Only services talk to the database; the web app only routes and forwards the cookie, and each service validates it with the shared `JWT_SECRET`. `orders-service` and `trading-service` also call each other directly (prices, sell validation and settlement one way; trade history and buying power the other), bypassing the web apps. After an order transition commits, `orders-service` publishes it to Kafka; that is one-way and no request depends on it. The consumers write only their own tables.

**Staff flow:** staff app → {`auth-service`, `orders-service`, `staff-ops-service`} → `db`. The staff app's Nginx forwards the browser's `staff_jwt` cookie to the services as `jwt` and drops any customer `jwt`, so services validate staff exactly as they validate clients.

## Shared Contracts (C1–C7): read before coding

[contracts/](contracts/README.md) defines what every feature codes against. Read the ones your change touches first:
- order statuses and lifecycle (C1)
- the audit event format (C2)
- seed personas and data (C3)
- quote-feed test controls (C4)
- business settings (C5)
- REST endpoints, internal events and the execution interface (C6)
- roles (C7)

Value lists and defaults live once, in code; the contract docs link to them. When you change a contract's code, update its doc in the same change. Never copy a list or a default into another file.

## Repository Layout

```
LeMarketJames/
├── pom.xml                  # Parent pom: module list, shared versions and plugins
├── libs/
│   ├── common/              # Shared jar used by the servlet services
│   └── market-client/       # MarketDataService contract, model, and an HTTP client implementation
├── services/
│   ├── auth-service/        # :8082
│   ├── trading-service/     # :8084  market, quotes, holdings, cash, notifications, market activity
│   ├── orders-service/      # :8085
│   └── staff-ops-service/   # :8086  analyst reports and surveillance alerts; staff app only
├── apps/
│   ├── frontend/            # Angular workspace: trading SPA (:4200) in src/, staff SPA (:4201) in projects/staff/,
│   │                        #   UI building blocks both use in projects/shared-ui/ (@lemarket/shared-ui)
│   └── e2e/                 # Playwright end-to-end tests (tests/, tests-staff/, load/), run against a live stack
├── contracts/               # Shared contracts C1–C7 (read before coding)
├── database/schema/         # Shared schema, numbered SQL files (011 is the seed data set)
├── scripts/windows/         # Native Windows run scripts (no Docker): setup-db.ps1, start-all.ps1, stop-all.ps1
├── docker-compose.yml
├── sonar-project.properties # SonarQube analysis settings for the whole repo (README: SonarQube)
└── Jenkinsfile
```

Every library and service has its own `pom.xml` that inherits from the root parent pom.

## Quick Commands

Run Maven commands from the repo root.

| Task | Command |
|---|---|
| All backend tests | `mvn -B clean test` |
| One module's tests | `mvn -B -pl services/auth-service -am test` |
| Run a service | `mvn -B -pl libs/common install` once, then `mvn -B -pl services/trading-service spring-boot:run` |
| Frontend tests (both apps) | `cd apps/frontend && ng test` |
| Frontend build | `cd apps/frontend && ng build` |
| Staff app tests / build / dev server | `cd apps/frontend && ng test staff` / `ng build staff` / `ng serve staff` |
| End-to-end tests (stack must be running on :4200) | `cd apps/e2e && npm install && npm test` |
| Staff app end-to-end tests (stack must be running on :4201) | `cd apps/e2e && npm install && npm run test:staff` |
| Report load test (stack on :4200 and :4201, `SIM_RESPECT_MARKET_HOURS=false`) | `cd apps/e2e && npm install && npm run test:load` |
| Full stack (Docker, Linux/Jenkins) | `docker compose up -d --build` |
| Full stack (native Windows, no Docker) | `.\scripts\windows\setup-db.ps1` once, then `.\scripts\windows\start-all.ps1`; stop with `.\scripts\windows\stop-all.ps1` |

**Existing database?** `setup-db.ps1` and Docker only apply `database/schema` to a fresh database. A service refuses to start (`ddl-auto=validate`) when a table or column is missing, so after pulling new numbered SQL files, apply them with `psql` ([database/README.md](database/README.md)); the recent ones are idempotent.

## Adding a New Microservice

1. Create `services/<name>-service/` with its own `pom.xml`: parent `lemarketjames-parent`, `<relativePath>../../pom.xml</relativePath>`, and a `common` dependency if it's a servlet service.
2. Add the folder to `<modules>` in the root `pom.xml`. First ask whether the feature belongs in an existing service: the stack was consolidated from thirteen services to four on purpose.
3. Put the `@SpringBootApplication` class in the root `com.lemarketjames` package so it picks up `com.lemarketjames.common.*`.
4. Point it at the shared database (`SPRING_DATASOURCE_*`) as `lemarket_app`, the restricted account that can't change audit records ([C2](contracts/C2-audit.md#the-lockdown)); never the owner `lemarket`. Give it the same `JWT_SECRET`, and the same `INTERNAL_API_TOKEN` if it serves or calls `/internal/**` (every `/internal/**` path needs it, [C6](contracts/C6-api.md#internal-endpoints-service-to-service-only-never-through-a-web-app)), and keep `spring.jpa.hibernate.ddl-auto=validate`. Schema changes still go in `database/schema`.
5. Add a route for its paths in each place the calling app routes from: the trading app's `apps/frontend/nginx.conf` and `proxy.conf.json` (above the trading-service catch-all), or the staff app's `projects/staff/nginx.conf` and `staff-proxy.mjs`. A staff-only service gets no trading-app route. Add its paths to [C6](contracts/C6-api.md#which-web-app-routes-which-paths).
6. Add a `Dockerfile` (copy an existing service's), a service in `docker-compose.yml`, and the name to the Jenkinsfile: a unit-test stage, the image loop, the smoke test's health ports and the `compose logs` lists. The "Show running containers" stage then expects its container without being told. Add it to `scripts/windows/start-all.ps1` and `stop-all.ps1` too, and its `target/surefire-reports` folder to `sonar.junit.reportPaths` in `sonar-project.properties`, which lists them module by module.

## Feature Dependencies (Keep Acyclic)

- **Auth** → Self-contained, required by everything. The endpoints live in `auth-service`. Other services only use `libs/common` (JWT validation, shared account entities) and never call auth-service directly.
- **Market** → Self-contained simulated price source. The engine lives in `trading-service`; `orders-service` reaches it through `libs/market-client`'s `MarketDataService` (an HTTP client, `MarketDataClient`), and trading-service's own quotes, holdings and charts read it in-process.
- **Orders** → Auth, Market. Placement/lifecycle lives in `orders-service`; it triggers settlement in `trading-service` when an order fills, and `trading-service` reads order data back from `orders-service` for trade history and buying power (both server-to-server, not through a web app).
- **Holdings, cash, portfolio** → Auth, Market, Orders (reads only). In `trading-service`.
- **Quotes, Instruments, Sessions** → Auth, Market. In `trading-service`, using the shared `Instrument` mapping in `libs/common`.
- **Watchlist** → Auth, Instruments. In `trading-service` (`com.lemarketjames.watchlist`): it stores tickers only, and the trading app pairs them with the quotes it already polls.
- **Audit** → nothing. `libs/common`'s `AuditRecorder` is called by Orders and by Holdings settlement and cash movements inside their own transactions ([C2](contracts/C2-audit.md)).
- **Reports** → Auth (roles only). `staff-ops-service` reads the `reporting_trades` view (plus `report_runs` for the overnight list) and calls no other service; no service calls it ([rules](services/staff-ops-service/README.md#rules-for-every-report-endpoint)).
- **Order event consumers** (Notifications and Market activity in trading-service; Surveillance in staff-ops-service) → the order events on Kafka, and Auth (roles only). The topic names and the setting that switches listening on live in `libs/common` (`com.lemarketjames.common.events`), so a consumer never depends on `orders-service`. A consumer reads an event with a record of its own, stores the same event only once, and has no foreign key to the tables of other services ([rules](contracts/C6-api.md#the-consumers-notifications-surveillance-and-market-activity)).
- **Roles** → Auth. They travel in the JWT, and each service enforces them in its own `SecurityConfig` ([C7](contracts/C7-roles.md)). The web apps' proxies check no role; the staff app only limits which paths exist.

## Further Reading

- **[Shared contracts](contracts/README.md)**: order lifecycle, audit, seed data, quote feed, settings, API and events, roles
- **[Database migrations](database/README.md)**: schema versioning workflow and per-migration notes
- **[staff-ops-service README](services/staff-ops-service/README.md)**: the rules every report endpoint follows
- `docs/` holds generated Javadoc only

## General Principles

- **Feature-driven design:** Each feature is self-contained in its package/folder
- **Layered responsibility:** Repository (data), Service (logic), Controller/Component (HTTP/UI)
- **API contracts first:** Backend and frontend stay in sync via [contracts/C6-api.md](contracts/C6-api.md)
- **Secrets in env files:** Never commit credentials or API keys
- **Readability:** Include comments of why code exists to improve readability.

## SOLID Principles

Prioritize SOLID design in all code contributions:

- **SRP:** One responsibility per class/module.
- **OCP:** Extend base code via subclasses, don't modify base unless necessary.
- **LSP:** Subtypes must be fully substitutable.
- **ISP:** Prefer small, focused interfaces.
- **DIP:** Depend on abstractions; use dependency injection.

Favor composition over inheritance, low coupling, high cohesion, and testable designs. Reject god classes, fat interfaces, and hard-coded dependencies. Briefly note SOLID decisions when relevant.
