# AGENTS.md

Conventions for anyone (human or AI agent) working on this repo. Keep changes consistent with what's here so the codebase stays readable. 

## Backend (`apps/backend/src/main/java/com/lemarketjames`) — Spring Boot / Maven

### Feature-Based Packages

- **Core principle:** Each business capability gets its own package containing everything it needs: `AuthController`, `AuthService`, `dto/`, and `security/` all live under `auth/`. Do not split a feature across top-level `controller/`, `service/`, `dto/` folders.
- Adding a new feature (e.g. orders, accounts): create `com.lemarketjames.<feature>` with its own controller/service/dto/repository, mirroring the `auth/` package.
- `config/` holds cross-cutting, app-wide configuration only (e.g. `SecurityConfig`) — not feature logic.
- `common/` holds code shared by multiple features (global exception handling, shared response DTOs). Nothing feature-specific goes here.

### Entity Organization

- **Pattern A (Simple):** Use `entity/` folder for single-entity features (e.g., Orders). Place Controller, Service, Repository, entity/, dto/ all in feature package.
- **Pattern B (Complex):** Use `domain/` folder for aggregate roots with value objects (e.g., Auth with Account, Client, Address). Same package structure, but entities go in domain/.

### Repository vs. Service Pattern

- **Repository:** Data access only. `<feature>/repository/<Feature>Repository.java` queries and persists. **Never put business logic here.**
- **Service:** Business logic and orchestration. `<feature>/<Feature>Service.java` validates, calls repositories, calls other services. **Never query DB directly here.**
- **Controller:** HTTP handling only. `<feature>/<Feature>Controller.java` routes requests, calls services, returns responses. **Never put business logic here.**

### Exception Handling

- **Feature-Specific Exceptions:** Create `<feature>/exception/` (e.g., `InsufficientHoldingsException`). Register `@ExceptionHandler` in `common/GlobalExceptionHandler.java`.
- **Shared Exceptions:** `ValidationException`, `NotFoundException` live in `common/`. Register handlers in GlobalExceptionHandler.

### Component Scanning

Spring scans `com.lemarketjames.*` automatically (rooted at `Main.java`). New feature packages are picked up without config changes: just ensure the package is under `com.lemarketjames.<feature>`.

### Testing

Tests mirror main structure: `apps/backend/src/test/java/com/lemarketjames/<feature>/` mirrors `apps/backend/src/main/java/com/lemarketjames/<feature>/`. Naming: `<Feature>ControllerTest.java`, `<Feature>ServiceTest.java`, `<Feature>RepositoryTest.java`. Run: `cd apps/backend && mvn -B clean test`

## Frontend (`apps/frontend/src/app`) — Angular

- **`core/`** — app-wide singletons: auth state, HTTP interceptors, guards, injectable data-access services. Imported once, never per-feature.
- **`shared/`** — reusable components/pipes/directives/models. Must not depend on a specific feature or hold app-wide state.
- **`features/<feature-name>/<component-name>/`** — routed, feature-specific UI (e.g., `features/auth/login`, `features/auth/register`).
- Services return Observables (RxJS); components subscribe. Inject services via constructor. Unsubscribe using async pipe or takeUntil.

## Database (`database/`)

Raw SQL, no migration tool by design. `schema/` holds numbered SQL files. **Never edit old files (001, 002, etc.); create new numbered files** for schema changes: `005_add_orders_table.sql`. Docker applies all files in order on startup.

## Commands

| Task | Command |
|---|---|
| Backend tests | `cd apps/backend && mvn -B clean test` |
| Backend run | `cd apps/backend && mvn spring-boot:run` |
| Frontend tests | `cd apps/frontend && ng test` |
| Frontend build | `cd apps/frontend && ng build` |
| Full stack (Docker), 3 services: frontend:4200, backend:8081, db:5432 | `docker compose up -d --build` |

## Runtime topology

Three separate processes/containers, each on its own port: `frontend` (4200) → `backend` (8081) → `db` (5432). The browser only ever talks to `frontend` and `backend`; only `backend` talks to `db`. Don't add direct frontend→db calls.

## API Versioning

All endpoints use `/api/v1/` prefix. Example: `/api/v1/orders`, `/api/v1/auth/login`. When implementing: use `/api/v1/` in controller route mapping and frontend service calls. Document in API-CONTRACTS.md with the `/api/v1/` prefix.

## API Contracts

**🔗 Reference:** `/API-CONTRACTS.md` is the single source of truth for all API endpoints, request/response formats, and team agreements.

- Backend: Implement endpoints to match the contract exactly. Every field must be present; field names case-sensitive.
- Frontend: Build UI against contract; mock services first, swap real service when backend is ready.
- Change Management: Don't change the contract unilaterally. Propose to team, update API-CONTRACTS.md and code synchronously.

## Feature Dependencies

Features can depend on other features. Keep dependencies acyclic.

- **Auth:** Self-contained. No dependencies on other features.
- **Orders:** May depend on Auth (user context), Holdings (inventory), Quotes (pricing).
- **Holdings:** May depend on Auth (user context). Called by Orders.
- **Quotes:** External data source. Minimal dependencies; called by Orders for pricing.
- **Sessions:** Depends on Auth (session management).

How: Services call other services via constructor injection. Document dependencies in comments.

## Common Pitfalls (and How to Avoid Them)

❌ **Don't split a feature across top-level folders** — Right: Everything under `com.lemarketjames.orders/`
❌ **Don't put business logic in Repository classes** — Repositories = data access only
❌ **Don't add feature-specific code to config/ or common/** — Put it in the feature package
❌ **Don't query the database directly in Services** — Use repositories
❌ **Don't make frontend components import from core/ unless accessing global state**
❌ **Don't deviate from API contracts without team approval**
❌ **Don't forget to register feature-specific exceptions in GlobalExceptionHandler**
❌ **Don't edit old database/schema/ files; create new numbered files** for schema changes
❌ **Don't assume component scanning works with wrong package structure** — Use `com.lemarketjames.*`

## General

- Keep files small and single-responsibility — a class/component should do one thing.
- Mirror naming across layers where it helps (e.g. `auth` package ↔ `features/auth` folder), but don't force a 1:1 mapping where it doesn't make sense.
- Always document and comment while developing for better readability 
- Develop with security as a priority
