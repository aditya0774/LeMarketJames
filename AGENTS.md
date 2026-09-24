# AGENTS.md

**LeMarketJames:** A full-stack trading application with an Angular frontend, Spring Boot microservices, and one shared PostgreSQL database, kept together in a single monorepo. Conventions keep the codebase readable and development consistent.

## Architecture at a Glance

- **Frontend** (4200): `apps/frontend/src/app` — Angular SPA
- **Gateway** (8080): `services/gateway-service` — Spring Cloud Gateway; the only backend entry point for the frontend
- **Auth service** (8082): `services/auth-service` — registration, login, logout, `/api/auth/me`
- **Market service** (8083): `services/market-service` — the GBM price simulator, exposed at `/api/market/**` for server-to-server callers (no browser ever calls it directly)
- **Holdings service** (8084): `services/holdings-service` — client profile (`/api/v1/profile`), holdings (`/api/v1/holdings`), portfolio/balance aggregate (`/api/v1/portfolio`), and trade history (`/api/v1/trades`). Settles fills (debits/credits cash, updates holdings) via `POST /internal/holdings/settle`, called server-to-server by `buy-sell-service` and never routed through the gateway. Order placement itself lives in `buy-sell-service`.
- **Buy-sell service** (8085): `services/buy-sell-service` — order placement, validation, buy/sell execution, order status, and order history (`/api/v1/orders`, `/api/v1/buy-orders`). Validates SELL orders and triggers settlement in `holdings-service` via `POST /internal/holdings/validate` and `POST /internal/holdings/settle`, both server-to-server and never routed through the gateway.
- **Core service** (8081): `services/core-service` — every feature not yet extracted: quotes, sessions
- **Shared libraries**: `libs/common` — JWT (`JwtService`, `JwtAuthenticationFilter`), shared account/client entities, `GlobalExceptionHandler`; `libs/market-client` — the `MarketDataService` interface, its quote/instrument model, and a ready-made HTTP implementation (`MarketDataClient`) any servlet service can use for prices just by depending on this module
- **Database** (5432): `database/schema` — one PostgreSQL database shared by every service, versioned via numbered SQL files
- **API:** All endpoints use `/api/v1/` prefix

**Runtime flow:** `frontend` → `gateway-service` → {`auth-service`, `core-service`, `market-service`, `holdings-service`, `buy-sell-service`} → `db`. Only services talk to the database; the gateway just routes and forwards the `jwt` cookie, and each service validates it with the shared `JWT_SECRET`. `core-service` and `holdings-service` also call `market-service` directly for prices (server-to-server, bypassing the gateway); `buy-sell-service` and `holdings-service` also call each other directly (validation/settlement and trade-history/buying-power reads, respectively) — see `holdings-service`'s and `buy-sell-service`'s descriptions above.

## Repository Layout

```
LeMarketJames/
├── pom.xml                  # Parent pom: module list, shared versions and plugins
├── libs/
│   ├── common/              # Shared jar used by the servlet services
│   └── market-client/       # MarketDataService contract, model, and a shared HTTP client implementation
├── services/
│   ├── gateway-service/     # :8080  routes auth/market/holdings paths to their services, everything else → core
│   ├── auth-service/        # :8082
│   ├── market-service/      # :8083
│   ├── holdings-service/    # :8084
│   ├── buy-sell-service/    # :8085
│   └── core-service/        # :8081
├── apps/
│   └── frontend/            # Angular SPA (:4200)
├── database/schema/         # Shared schema, numbered SQL files
├── docker-compose.yml
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
| Frontend tests | `cd apps/frontend && ng test` |
| Frontend build | `cd apps/frontend && ng build` |
| Full stack (Docker) | `docker compose up -d --build` |

## Adding a New Microservice

1. Create `services/<name>-service/` with its own `pom.xml`: parent `lemarketjames-parent`, `<relativePath>../../pom.xml</relativePath>`, and a `common` dependency if it's a servlet service.
2. Add the folder to `<modules>` in the root `pom.xml`.
3. Put the `@SpringBootApplication` class in the root `com.lemarketjames` package so it picks up `com.lemarketjames.common.*`. Move the feature's packages and tests out of `core-service`.
4. Point it at the shared database (`SPRING_DATASOURCE_*`), give it the same `JWT_SECRET`, and keep `spring.jpa.hibernate.ddl-auto=validate`. Schema changes still go in `database/schema`.
5. Add a route for its paths in `services/gateway-service/src/main/resources/application.yml`, **above** the `core-service` catch-all.
6. Add a `Dockerfile` (copy an existing service's), a service in `docker-compose.yml`, and the name to the image loop in the Jenkinsfile.

## Feature Dependencies (Keep Acyclic)

- **Auth** → Self-contained, required by everything. The endpoints live in `auth-service`. Other services only use `libs/common` (JWT validation, shared account entities) and never call auth-service directly.
- **Market** → Self-contained simulated price source. The engine lives in `market-service`; other features reach it via `libs/market-client`'s `MarketDataService` interface (implemented as an HTTP client, `MarketDataClient`, shared by `core-service` and `holdings-service`).
- **Orders** → Auth. Placement/lifecycle lives in `buy-sell-service`; it triggers settlement in `holdings-service` when an order fills, and `holdings-service` reads order data back from `buy-sell-service` for trade history and buying power (both server-to-server, not through the gateway).
- **Holdings** → Auth, Market, Orders (reads only, via `holdings-service`'s `OrdersClient`)
- **Quotes** → Market
- **Sessions** → Auth

## Layer-Specific Conventions

- **[Backend Architecture](docs/BACKEND.md)** — Feature packages, Repository/Service pattern, exceptions, testing
- **[Frontend Architecture](docs/FRONTEND.md)** — Directory structure, RxJS observables, dependency injection
- **[Market Simulation](docs/MARKET.md)** — GBM price engine: model, state, configuration, how other features read prices
- **[Database Migrations](docs/DATABASE.md)** — Schema versioning workflow
- **[API Design](docs/API-DESIGN.md)** — Versioning, contracts, change management
- **[Guidelines & Pitfalls](docs/GUIDELINES.md)** — Common mistakes, best practices

## General Principles

- **Feature-driven design:** Each feature is self-contained in its package/folder
- **Layered responsibility:** Repository (data), Service (logic), Controller/Component (HTTP/UI)
- **API contracts first:** Backend and frontend stay in sync via `/API-CONTRACTS.md`
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