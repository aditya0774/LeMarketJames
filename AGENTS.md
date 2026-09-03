# AGENTS.md

Conventions for anyone (human or AI agent) working on this repo. Keep changes consistent with what's here so the codebase stays readable. 

## Backend (`apps/backend/src/main/java/com/lemarketjames`) — Spring Boot / Maven

- **Feature-based packages.** Each business capability gets its own package containing everything it needs: `AuthController`, `AuthService`, `dto/`, and `security/` all live under `auth/`. Do not split a feature across top-level `controller/`, `service/`, `dto/` folders.
- Adding a new feature (e.g. orders, accounts): create `com.lemarketjames.<feature>` with its own controller/service/dto/repository, mirroring the `auth/` package.
- `config/` holds cross-cutting, app-wide configuration only (e.g. `SecurityConfig`) — not feature logic.
- `common/` holds code shared by multiple features (global exception handling, shared response DTOs). Nothing feature-specific goes here.
- Tests under `apps/backend/src/test/java/com/lemarketjames` mirror the main package structure 1:1.
- Spring Boot component scanning is rooted at `Main.java`'s package (`com.lem------arketjames`), so new subpackages are picked up automatically — no `pom.xml`/config changes needed when adding a feature package.

## Frontend (`apps/frontend/src/app`) — Angular

- `core/` — app-wide singletons: auth state, HTTP interceptors, guards. Imported once, never per-feature.
- `shared/` — reusable, presentational components/pipes/directives/models used by multiple features. Must not depend on a specific feature or hold app-wide state.
- `features/<feature-name>/<component-name>/` — routed, feature-specific UI (e.g. `features/auth/login`, `features/auth/register`). Add new features as sibling folders here.

## Database (`database/`)

- Raw SQL, no migration tool by design.
- `schema/` holds numbered, ordered SQL files (`001_core_schema.sql`, `002_...`). Add new numbered files for schema changes; don't edit old ones in place.

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

## General

- Keep files small and single-responsibility — a class/component should do one thing.
- Mirror naming across layers where it helps (e.g. `auth` package ↔ `features/auth` folder), but don't force a 1:1 mapping where it doesn't make sense.
- Develop with security as a priority
