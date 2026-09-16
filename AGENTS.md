# AGENTS.md

**LeMarketJames:** A three-tier full-stack trading application with Angular frontend, Spring Boot backend, and PostgreSQL database. Conventions keep the codebase readable and development consistent.

## Architecture at a Glance

- **Frontend** (4200): `apps/frontend/src/app` — Angular SPA
- **Backend** (8081): `apps/backend/src/main/java/com/lemarketjames` — Spring Boot REST API
- **Database** (5432): `database/schema` — PostgreSQL, versioned via numbered SQL files
- **API:** All endpoints use `/api/v1/` prefix

**Runtime flow:** `frontend` → `backend` → `db` (only backend talks to database)

## Quick Commands

| Task | Command |
|---|---|
| Backend tests | `cd apps/backend && mvn -B clean test` |
| Backend run | `cd apps/backend && mvn spring-boot:run` |
| Frontend tests | `cd apps/frontend && ng test` |
| Frontend build | `cd apps/frontend && ng build` |
| Full stack (Docker) | `docker compose up -d --build` |

## Feature Dependencies (Keep Acyclic)

- **Auth** → Self-contained, required by everything
- **Orders** → Auth, Holdings, Quotes
- **Holdings** → Auth
- **Quotes** → External data source
- **Sessions** → Auth

## Layer-Specific Conventions

- **[Backend Architecture](docs/BACKEND.md)** — Feature packages, Repository/Service pattern, exceptions, testing
- **[Frontend Architecture](docs/FRONTEND.md)** — Directory structure, RxJS observables, dependency injection
- **[Database Migrations](docs/DATABASE.md)** — Schema versioning workflow
- **[API Design](docs/API-DESIGN.md)** — Versioning, contracts, change management
- **[Guidelines & Pitfalls](docs/GUIDELINES.md)** — Common mistakes, best practices

## General Principles

- **Feature-driven design:** Each feature is self-contained in its package/folder
- **Layered responsibility:** Repository (data), Service (logic), Controller/Component (HTTP/UI)
- **API contracts first:** Backend and frontend stay in sync via `/API-CONTRACTS.md`
- **Secrets in env files:** Never commit credentials or API keys
- **Readability:** Include comments of why code exists to improve readability.