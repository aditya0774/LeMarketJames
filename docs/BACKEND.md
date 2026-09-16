# Backend Architecture

Spring Boot REST API at `apps/backend/src/main/java/com/lemarketjames`. Feature-driven design with Repository/Service/Controller separation.

## Feature-Based Packages

**Core principle:** Each business capability is a self-contained package under `com.lemarketjames.<feature>`.

- `AuthController`, `AuthService`, `dto/`, `security/` all live under `auth/` — do not split across top-level `controller/`, `service/`, `dto/` folders
- Adding a feature: create `com.lemarketjames.<feature>` with controller/service/repository/dto (mirror the `auth/` package)
- `config/` holds app-wide configuration only (e.g., `SecurityConfig`) — not feature logic
- `common/` holds code shared by multiple features (global exception handling, shared response DTOs) — nothing feature-specific

## Entity Organization

Choose based on domain complexity:

- **Pattern A (Simple):** Use `entity/` folder for single-entity features (e.g., Orders). All classes (Controller, Service, Repository, entity/, dto/) live in the feature package.
- **Pattern B (Complex):** Use `domain/` folder for aggregate roots with value objects (e.g., Auth with Account, Client, Address). Same package structure, entities go in domain/ instead of entity/.

## Repository vs. Service vs. Controller

**Repository** — Data access only
- Lives in: `<feature>/repository/<Feature>Repository.java`
- Responsibility: Query database, map rows to entities, persist changes
- **Never put business logic here**

**Service** — Business logic and orchestration
- Lives in: `<feature>/<Feature>Service.java`
- Responsibility: Business logic, validation, calling repositories, calling other services
- **Never query the database directly here** — use repositories

**Controller** — HTTP handling only
- Lives in: `<feature>/<Feature>Controller.java`
- Responsibility: HTTP routing, parsing requests, calling services, returning responses
- **Never put business logic here** — delegate to services

## Exception Handling

**Feature-Specific Exceptions:** Create `<feature>/exception/` (e.g., `InsufficientHoldingsException`). Register `@ExceptionHandler` in `common/GlobalExceptionHandler.java`.

**Shared Exceptions:** `ValidationException`, `NotFoundException` live in `common/`. Register handlers in GlobalExceptionHandler.

All exceptions are caught and mapped to HTTP responses in `common/GlobalExceptionHandler.java`, ensuring consistent JSON error format across all endpoints.

## Component Scanning & Auto-Configuration

Spring scans `com.lemarketjames.*` automatically (rooted at `Main.java`). New feature packages are picked up without `pom.xml` or config changes: just ensure the package is under `com.lemarketjames.<feature>`.

## Testing

Tests mirror main structure: `apps/backend/src/test/java/com/lemarketjames/<feature>/` mirrors `apps/backend/src/main/java/com/lemarketjames/<feature>/`.

Naming convention:
- Controller test: `<Feature>ControllerTest.java` (e.g., `OrdersControllerTest.java`)
- Service test: `<Feature>ServiceTest.java` (e.g., `OrdersServiceTest.java`)
- Repository test: `<Feature>RepositoryTest.java`

Run all tests: `cd apps/backend && mvn -B clean test`

---

← Back to [AGENTS.md](../AGENTS.md)
