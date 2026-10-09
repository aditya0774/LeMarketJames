# AGENTS.md

**LeMarketJames:** A full-stack trading application with an Angular frontend, Spring Boot microservices, and one shared PostgreSQL database, kept together in a single monorepo. Conventions keep the codebase readable and development consistent.

## Architecture at a Glance

- **Frontend** (4200): `apps/frontend/src/app` — Angular SPA
- **Staff frontend** (4201): `apps/frontend/projects/staff/src/app` — separate Angular SPA for staff (Trading Ops, Analyst). A second project in the same Angular workspace as the trading frontend: same `package.json` and toolchain, no shared application code. Its backend entry point is the staff gateway (8090); it never calls the trading gateway. Like the trading frontend it calls `/api` on its own origin, forwarded by its own `proxy.conf.json` (dev server) or `nginx.conf` (Docker).
- **Gateway** (8080): `services/gateway-service` — Spring Cloud Gateway; the only backend entry point for the frontend
- **Staff gateway** (8090): the same `services/gateway-service` module started with the `staff` profile (`application-staff.yml`) — the only backend entry point for the staff frontend. It routes `/api/v1/reports/**` to `reporting-service`, sign-in, sign-out and `/api/auth/me` to `auth-service`, the Trading Ops trade search and order timeline to `buy-sell-service`, and `/api/v1/surveillance/**` to `surveillance-service`; nothing else. It also gives staff their own session cookie, `staff_jwt`, by renaming the services' `jwt` in both directions ([C7](contracts/C7-roles.md#the-staff-session-cookie)).
- **Auth service** (8082): `services/auth-service` — registration, login, logout, `/api/auth/me`
- **Market service** (8083): `services/market-service` — the GBM price simulator, exposed at `/api/market/**` for server-to-server callers (no browser ever calls it directly)
- **Holdings service** (8084): `services/holdings-service` — client profile (`/api/v1/profile`), holdings (`/api/v1/holdings`), portfolio/balance aggregate (`/api/v1/portfolio`), and trade history (`/api/v1/trades`). Settles fills (debits/credits cash, updates holdings) via `POST /internal/holdings/settle`, called server-to-server by `buy-sell-service` and never routed through the gateway. Order placement and execution live in `buy-sell-service`.
- **Buy-sell service** (8085): `services/buy-sell-service` — order placement, lifecycle, automatic execution and history; calls holdings for idempotent settlement.
- **Reporting service** (8086): `services/reporting-service` — aggregate, read-only reports for analysts under `/api/v1/reports/**`, built on the `reporting_trades` view. Not routed by the trading gateway: the staff gateway (8090) is its only caller. Its [README](services/reporting-service/README.md) holds the rules every report endpoint follows.
- **Notification service** (8087), **surveillance service** (8088), **activity service** (8091): `services/notification-service`, `services/surveillance-service`, `services/activity-service` — the three Kafka consumers. Each listens to one order event topic in its own consumer group, keeps its results in its own table and serves them from one read-only endpoint: a client's notifications about their orders (`/api/v1/notifications`), Trading Ops' alerts on large orders (`/api/v1/surveillance/alerts`, staff gateway only) and the traded volume per stock (`/api/v1/market-activity`). They call no service and no service calls them ([C6](contracts/C6-api.md#the-consumers-notification--surveillance--and-activity-service)).
- **Core service** (8081): `services/core-service` — quotes, instruments, sessions
- **Shared libraries**: `libs/common` — JWT (`JwtService`, `JwtAuthenticationFilter`), shared account/client entities, `GlobalExceptionHandler`; `libs/market-client` — the `MarketDataService` interface, its quote/instrument model, and a ready-made HTTP implementation (`MarketDataClient`) any servlet service can use for prices just by depending on this module
- **Database** (5432): `database/schema` — one PostgreSQL database shared by every service, versioned via numbered SQL files
- **Kafka** (9092): a single-node broker (KRaft, no ZooKeeper) defined in `docker-compose.yml`. `buy-sell-service` publishes its order events to it in Docker and Jenkins; without a broker (unit tests, the native Windows scripts) it logs them instead ([C6](contracts/C6-api.md#internal-events-and-the-execution-interface-buy-sell-service), setting in [C5](contracts/C5-config.md#order-events)). The three consumer services above read them from it, under the matching setting.
- **API:** All endpoints use `/api/v1/` prefix

**Runtime flow:** `frontend` → `gateway-service` → {`auth-service`, `core-service`, `market-service`, `holdings-service`, `buy-sell-service`, `notification-service`, `activity-service`} → `db`. Only services talk to the database; the gateway just routes and forwards the `jwt` cookie, and each service validates it with the shared `JWT_SECRET`. `core-service` and `holdings-service` also call `market-service` directly for prices (server-to-server, bypassing the gateway); `buy-sell-service` and `holdings-service` also call each other directly (settlement and trade-history/buying-power reads, respectively) — see `holdings-service`'s description above. `reporting-service` sits outside this flow: it reads the same database but is reached only through the staff gateway, never the trading one. After an order transition commits, `buy-sell-service` also publishes it to Kafka; that is one-way and no request depends on it. `notification-service`, `surveillance-service` and `activity-service` consume those events and write only their own tables.

**Staff flow:** `staff frontend` → staff gateway → {`auth-service`, `reporting-service`, `buy-sell-service`, `surveillance-service`} → `db`. The staff gateway forwards the browser's `staff_jwt` cookie to the services as `jwt` and drops any customer `jwt`, so services validate staff exactly as they validate clients.

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
│   ├── gateway-service/     # :8080  routes auth/market/holdings paths to their services, everything else → core
│   │                        # :8090  with the "staff" profile: the staff gateway (staff routes only)
│   ├── auth-service/        # :8082
│   ├── market-service/      # :8083
│   ├── holdings-service/    # :8084
│   ├── buy-sell-service/    # :8085
│   ├── reporting-service/   # :8086  analyst reports; no trading-gateway route
│   ├── notification-service/  # :8087  Kafka consumer: a client's order notifications
│   ├── surveillance-service/  # :8088  Kafka consumer: large-order alerts for Trading Ops; no trading-gateway route
│   ├── activity-service/    # :8091  Kafka consumer: traded volume per stock
│   └── core-service/        # :8081
├── apps/
│   ├── frontend/            # Angular workspace: trading SPA (:4200) in src/, staff SPA (:4201) in projects/staff/
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
| Run a service | `mvn -B -pl libs/common install` once, then `mvn -B -pl services/core-service spring-boot:run` |
| Run the staff gateway | `mvn -B -pl services/gateway-service spring-boot:run "-Dspring-boot.run.profiles=staff"` |
| Frontend tests (both apps) | `cd apps/frontend && ng test` |
| Frontend build | `cd apps/frontend && ng build` |
| Staff app tests / build / dev server | `cd apps/frontend && ng test staff` / `ng build staff` / `ng serve staff` |
| End-to-end tests (stack must be running on :4200) | `cd apps/e2e && npm install && npm test` |
| Staff app end-to-end tests (stack must be running on :4201) | `cd apps/e2e && npm install && npm run test:staff` |
| Full stack (Docker, Linux/Jenkins) | `docker compose up -d --build` |
| Full stack (native Windows, no Docker) | `.\scripts\windows\setup-db.ps1` once, then `.\scripts\windows\start-all.ps1`; stop with `.\scripts\windows\stop-all.ps1` |

## Adding a New Microservice

1. Create `services/<name>-service/` with its own `pom.xml`: parent `lemarketjames-parent`, `<relativePath>../../pom.xml</relativePath>`, and a `common` dependency if it's a servlet service.
2. Add the folder to `<modules>` in the root `pom.xml`.
3. Put the `@SpringBootApplication` class in the root `com.lemarketjames` package so it picks up `com.lemarketjames.common.*`. Move the feature's packages and tests out of `core-service`.
4. Point it at the shared database (`SPRING_DATASOURCE_*`) as `lemarket_app`, the restricted account that can't change audit records ([C2](contracts/C2-audit.md#the-lockdown)); never the owner `lemarket`. Give it the same `JWT_SECRET`, and keep `spring.jpa.hibernate.ddl-auto=validate`. Schema changes still go in `database/schema`.
5. Add a route for its paths in `services/gateway-service/src/main/resources/application.yml`, **above** the `core-service` catch-all. A staff-only service such as `reporting-service` gets no route here; add its route to the staff gateway's list in `application-staff.yml` in the same folder, and its paths to [C6](contracts/C6-api.md#which-gateway-serves-which-paths).
6. Add a `Dockerfile` (copy an existing service's), a service in `docker-compose.yml`, and the name to the Jenkinsfile: a unit-test stage, the image loop, the smoke test's health ports and the `compose logs` lists. The "Show running containers" stage then expects its container without being told. Add it to `scripts/windows/start-all.ps1` and `stop-all.ps1` too, and its `target/surefire-reports` folder to `sonar.junit.reportPaths` in `sonar-project.properties`, which lists them module by module.

## Feature Dependencies (Keep Acyclic)

- **Auth** → Self-contained, required by everything. The endpoints live in `auth-service`. Other services only use `libs/common` (JWT validation, shared account entities) and never call auth-service directly.
- **Market** → Self-contained simulated price source. The engine lives in `market-service`; other features reach it via `libs/market-client`'s `MarketDataService` interface (implemented as an HTTP client, `MarketDataClient`, shared by `core-service` and `holdings-service`).
- **Orders** → Auth. Placement/lifecycle lives in `buy-sell-service`; it triggers settlement in `holdings-service` when an order fills, and `holdings-service` reads order data back from `buy-sell-service` for trade history and buying power (both server-to-server, not through the gateway).
- **Holdings** → Auth, Market, Orders (reads only, via `holdings-service`'s `OrdersClient`)
- **Quotes** → Market
- **Sessions** → Auth
- **Instruments** → Auth. The supported stock list (`GET /api/v1/instruments`) lives in `core-service`, using the shared `Instrument` mapping in `libs/common`.
- **Audit** → nothing. `libs/common`'s `AuditRecorder` is called by Orders (buy-sell-service) and Holdings settlement (holdings-service) inside their own transactions ([C2](contracts/C2-audit.md)).
- **Reports** → Auth (roles only). `reporting-service` reads the `reporting_trades` view and nothing else; it calls no other service and no service calls it ([rules](services/reporting-service/README.md#rules-for-every-report-endpoint)).
- **Order event consumers** (Notifications, Surveillance, Market activity) → the order events on Kafka, and Auth (roles only). The topic names and the setting that switches listening on live in `libs/common` (`com.lemarketjames.common.events`), so a consumer never depends on `buy-sell-service`. A consumer reads an event with a record of its own, stores the same event only once, and has no foreign key to the tables of other services ([rules](contracts/C6-api.md#the-consumers-notification--surveillance--and-activity-service)).
- **Roles** → Auth. They travel in the JWT, and each service enforces them in its own `SecurityConfig` ([C7](contracts/C7-roles.md)). Neither gateway checks a role; the staff gateway only limits which paths exist.

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
