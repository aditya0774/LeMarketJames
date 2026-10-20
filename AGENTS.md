# AGENTS.md

**LeMarketJames:** A full-stack trading application with an Angular frontend, Spring Boot microservices, and one shared PostgreSQL database, kept together in a single monorepo. Conventions keep the codebase readable and development consistent.

## Architecture at a Glance

Seven services: two web apps, four Spring Boot microservices and a Kafka broker, plus the shared PostgreSQL database.

- **Trading app** (4200): `apps/frontend/src/app` — Angular SPA for clients. It calls `/api` on its own origin; its `nginx.conf` (Docker) or `proxy.conf.json` (dev server) forwards each path straight to the service that serves it. There is no gateway.
- **Staff app** (4201): `apps/frontend/projects/staff/src/app` — separate Angular SPA for staff (Trading Ops, Analyst). A second project in the same Angular workspace as the trading app: same `package.json` and toolchain. The two share no features, routes or services; the UI building blocks both show the same way (the theme service and the account menu) live once in `apps/frontend/projects/shared-ui`, imported as `@lemarket/shared-ui`. Its own `nginx.conf` (Docker) or `staff-proxy.mjs` (dev server) forwards only the staff paths: sign-in, sign-out and `/api/auth/me` to `auth-service`, the Trading Ops trade search and order timeline to `orders-service`, and `/api/v1/reports/**` and `/api/v1/surveillance/**` to `staff-ops-service`; anything else answers 404. It also gives staff their own session cookie, `staff_jwt`, by renaming the services' `jwt` in both directions ([C7](contracts/C7-roles.md#the-staff-session-cookie)).
- **Auth service** (8082): `services/auth-service` — registration, login, logout, `/api/auth/me`
- **Orders service** (8085): `services/orders-service` — order placement, lifecycle, automatic execution and history, and the Trading Ops trade search and order timeline. Reads prices from and settles fills in `trading-service`, and publishes the order events to Kafka.
- **Trading service** (8084): `services/trading-service` — Trading/Holdings: everything a client trades against apart from the orders themselves.
  - The GBM market simulator, exposed at `/api/market/**` for `orders-service`; this service's own features read it in-process (`com.lemarketjames.market.inprocess`).
  - Quotes, instruments and sessions; the client profile (`/api/v1/profile`), holdings (`/api/v1/holdings`), portfolio/balance aggregate (`/api/v1/portfolio`) and trade history (`/api/v1/trades`).
  - Settles fills (debits/credits cash, updates holdings) via `POST /internal/holdings/settle`, called server-to-server by `orders-service` and never forwarded by a web app.
  - Two Kafka consumers: a client's notifications about their orders (`/api/v1/notifications`) and the traded volume per stock (`/api/v1/market-activity`).
  - Everything under `/api/**` that no other service claims reaches it through the trading app.
- **Staff ops service** (8086): `services/staff-ops-service` — Analyst/Trading Ops: aggregate, read-only reports for analysts under `/api/v1/reports/**`, built on the `reporting_trades` view, and the Kafka consumer that raises Trading Ops' alerts on large orders and refused settlements (`/api/v1/surveillance/alerts`). Only the staff app routes to it. Reports read through a connection pool of their own that is small, read-only and time-limited; alerts are written through the ordinary one. Its [README](services/staff-ops-service/README.md) holds the rules every report endpoint follows.
- **Broker: Kafka** (9092): a single-node broker (KRaft, no ZooKeeper) defined in `docker-compose.yml`. `orders-service` publishes its order events to it in Docker and Jenkins; without a broker (unit tests, the native Windows scripts) it logs them instead ([C6](contracts/C6-api.md#internal-events-and-the-execution-interface-orders-service), setting in [C5](contracts/C5-config.md#order-events)). The three consumers (notifications, market activity, surveillance) read them from it, under the matching setting, each in a consumer group of its own ([C6](contracts/C6-api.md#the-consumers-notifications-surveillance-and-market-activity)).
- **Shared libraries**: `libs/common` — JWT (`JwtService`, `JwtAuthenticationFilter`), shared account/client entities, `GlobalExceptionHandler`; `libs/market-client` — the `MarketDataService` interface, its quote/instrument model, and a ready-made HTTP implementation (`MarketDataClient`) any servlet service can use for prices just by depending on this module
- **Database** (5432): `database/schema` — one PostgreSQL database shared by every service, versioned via numbered SQL files. Infrastructure, not one of the seven services.
- **API:** All endpoints use `/api/v1/` prefix

**Runtime flow:** `trading app` → {`auth-service`, `orders-service`, `trading-service`} → `db`. Only services talk to the database; the trading app's Nginx just routes by path and passes the `jwt` cookie on, and each service validates it with the shared `JWT_SECRET`. `orders-service` and `trading-service` also call each other directly, server-to-server: prices and settlement one way, trade-history and buying-power reads the other. After an order transition commits, `orders-service` also publishes it to Kafka; that is one-way and no request depends on it. The consumers in `trading-service` and `staff-ops-service` read those events and write only their own tables.

**Staff flow:** `staff app` → {`auth-service`, `orders-service`, `staff-ops-service`} → `db`. The staff app sends the browser's `staff_jwt` cookie to the services as `jwt` and drops any customer `jwt`, so services validate staff exactly as they validate clients. `staff-ops-service` calls no service and no service calls it.

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
│   └── market-client/       # MarketDataService contract, model, and a shared HTTP client implementation
├── services/
│   ├── auth-service/        # :8082
│   ├── orders-service/      # :8085  orders, execution, Trading Ops search and timeline; publishes order events
│   ├── trading-service/     # :8084  market simulator, quotes, instruments, sessions, holdings, portfolio,
│   │                        #        trade history; Kafka consumers: notifications, market activity
│   └── staff-ops-service/   # :8086  analyst reports and Trading Ops surveillance alerts (Kafka consumer);
│                            #        staff app only
├── apps/
│   ├── frontend/            # Angular workspace: trading SPA (:4200) in src/, staff SPA (:4201) in projects/staff/,
│   │                        #   UI building blocks both use in projects/shared-ui/ (@lemarket/shared-ui);
│   │                        #   each app's nginx.conf and dev proxy route /api to the services
│   └── e2e/                 # Playwright end-to-end tests, run against a live stack
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
| Run a service | `mvn -B -pl libs/common,libs/market-client install` once, then `mvn -B -pl services/trading-service spring-boot:run` |
| Frontend tests (both apps) | `cd apps/frontend && ng test` |
| Frontend build | `cd apps/frontend && ng build` |
| Staff app tests / build / dev server | `cd apps/frontend && ng test staff` / `ng build staff` / `ng serve staff` |
| End-to-end tests (stack must be running on :4200) | `cd apps/e2e && npm install && npm test` |
| Staff app end-to-end tests (stack must be running on :4201) | `cd apps/e2e && npm install && npm run test:staff` |
| Report load test (stack on :4200 and :4201, `SIM_RESPECT_MARKET_HOURS=false`) | `cd apps/e2e && npm install && npm run test:load` |
| Full stack (Docker, Linux/Jenkins) | `docker compose up -d --build` |
| Full stack (native Windows, no Docker) | `.\scripts\windows\setup-db.ps1` once, then `.\scripts\windows\start-all.ps1`; stop with `.\scripts\windows\stop-all.ps1` |

## Adding a New Microservice

1. Create `services/<name>-service/` with its own `pom.xml`: parent `lemarketjames-parent`, `<relativePath>../../pom.xml</relativePath>`, and a `common` dependency if it's a servlet service.
2. Add the folder to `<modules>` in the root `pom.xml`.
3. Put the `@SpringBootApplication` class in the root `com.lemarketjames` package so it picks up `com.lemarketjames.common.*`. Move the feature's packages and tests out of the service that held them. Before adding a service at all, check that the feature doesn't belong in one of the four that exist: the stack is kept to seven services on purpose.
4. Point it at the shared database (`SPRING_DATASOURCE_*`) as `lemarket_app`, the restricted account that can't change audit records ([C2](contracts/C2-audit.md#the-lockdown)); never the owner `lemarket`. Give it the same `JWT_SECRET`, and keep `spring.jpa.hibernate.ddl-auto=validate`. Schema changes still go in `database/schema`.
5. Route its paths in the web app that serves them: for the trading app in `apps/frontend/nginx.conf` and `apps/frontend/proxy.conf.json`/`proxy.vm.json`, **above** the `trading-service` catch-all; for the staff app in `apps/frontend/projects/staff/nginx.conf` and `staff-proxy.mjs`. A staff-only service gets no trading-app route. Add its paths to [C6](contracts/C6-api.md#which-web-app-routes-which-paths).
6. Add a `Dockerfile` (copy an existing service's), a service in `docker-compose.yml` (and to the `depends_on` of the web app that routes to it), and the name to the Jenkinsfile: a unit-test stage, the image loop, the smoke test's health ports and the `compose logs` lists. The "Show running containers" stage then expects its container without being told. Add it to `scripts/windows/start-all.ps1` and `stop-all.ps1` too, and its `target/surefire-reports` folder to `sonar.junit.reportPaths` in `sonar-project.properties`, which lists them module by module.

## Feature Dependencies (Keep Acyclic)

- **Auth** → Self-contained, required by everything. The endpoints live in `auth-service`. Other services only use `libs/common` (JWT validation, shared account entities) and never call auth-service directly.
- **Market** → Self-contained simulated price source. The engine lives in `trading-service`; other features reach it via `libs/market-client`'s `MarketDataService` interface: in-process inside `trading-service` (`InProcessMarketData`, which also reports feed outages the way the HTTP client does), and over HTTP from `orders-service` (`MarketDataClient`).
- **Orders** → Auth, Market. Placement/lifecycle lives in `orders-service`; it triggers settlement in `trading-service` when an order fills, and `trading-service` reads order data back from `orders-service` for trade history and buying power (both server-to-server, not through a web app).
- **Holdings** → Auth, Market, Orders (reads only, via `trading-service`'s `OrdersClient`)
- **Quotes** → Market
- **Sessions** → Auth
- **Instruments** → Auth. The supported stock list (`GET /api/v1/instruments`) lives in `trading-service`, using the shared `Instrument` mapping in `libs/common`.
- **Audit** → nothing. `libs/common`'s `AuditRecorder` is called by Orders (orders-service) and Holdings settlement (trading-service) inside their own transactions ([C2](contracts/C2-audit.md)).
- **Reports** → Auth (roles only). The reports in `staff-ops-service` read the `reporting_trades` view and nothing else, through their own read-only pool; they call no other service ([rules](services/staff-ops-service/README.md#rules-for-every-report-endpoint)).
- **Order event consumers** (Notifications and Market activity in `trading-service`, Surveillance in `staff-ops-service`) → the order events on Kafka, and Auth (roles only). The topic names and the setting that switches listening on live in `libs/common` (`com.lemarketjames.common.events`), so a consumer never depends on `orders-service`. A consumer reads an event with a record of its own, stores the same event only once, and has no foreign key to the tables of other features ([rules](contracts/C6-api.md#the-consumers-notifications-surveillance-and-market-activity)).
- **Roles** → Auth. They travel in the JWT, and each service enforces them in its own `SecurityConfig` ([C7](contracts/C7-roles.md)). Neither web app checks a role; the staff app only limits which paths exist.

## Further Reading

- **[Shared contracts](contracts/README.md)**: order lifecycle, audit, seed data, quote feed, settings, API and events, roles
- **[Database migrations](database/README.md)**: schema versioning workflow and per-migration notes
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
