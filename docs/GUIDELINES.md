# Guidelines & Common Pitfalls

Practical dos and don'ts for feature implementation in LeMarketJames.

## Common Implementation Mistakes

### Backend

❌ **Don't split a feature across top-level folders**
- Wrong: `controllers/OrderController.java`, `services/OrderService.java`, `dto/OrderRequest.java`
- Right: Everything under `com.lemarketjames.orders/` with its own package structure

❌ **Don't put business logic in Repository classes**
- Wrong: `OrderRepository.calculateDiscount()` or `OrderRepository.validateOrder()`
- Right: Repository = data access only. Business logic goes in OrderService.

❌ **Don't query the database directly in Services**
- Wrong: Service uses `@Autowired JdbcTemplate` or raw SQL
- Right: Service calls Repository methods; Repository handles database access

❌ **Don't add feature-specific code to config/ or common/**
- Wrong: `config/OrdersConfig.java` or `common/OrderHelper.java`
- Right: Feature-specific config and helpers go in the feature package

❌ **Don't forget to register feature-specific exceptions in GlobalExceptionHandler**
- Wrong: Throw `InsufficientHoldingsException` but no handler in `GlobalExceptionHandler`
- Right: Create exception in `<feature>/exception/`, add `@ExceptionHandler` in `GlobalExceptionHandler`

❌ **Don't assume component scanning works without proper package structure**
- Wrong: Create `com.other.package.OrderService` and expect Spring to find it
- Right: Ensure all features are under `com.lemarketjames.*` (scanned automatically from `Main.java`)

### Frontend

❌ **Don't import from core/ unless accessing global state**
- Wrong: Feature component imports `OrderService` from core, then queries it directly
- Right: Feature component injects `OrderService` via constructor; service handles the call

❌ **Don't forget to unsubscribe from Observables**
- Wrong: Subscribe to Observable in ngOnInit, never unsubscribe → memory leaks
- Right: Use async pipe in templates or takeUntil in component logic

### Database

❌ **Don't edit old database/schema/ files to add changes**
- Wrong: Edit `001_core_schema.sql` to add new columns
- Right: Create `005_add_new_table.sql` (new numbered file) with the change

### API & Contracts

❌ **Don't deviate from API contracts without team approval**
- Wrong: Add extra fields to response, change endpoint path, alter request structure unilaterally
- Right: Changes must be approved by the team and updated in `/API-CONTRACTS.md` synchronously

---

## Architecture Decisions

### Why Feature-Based Packages?

Keeps all code for a business capability (Auth, Orders, Holdings) in one place. Easier to understand, modify, and test a complete feature without hunting across folder hierarchies.

### Why Repository/Service/Controller Separation?

Enforces single responsibility: Repository handles data access, Service handles business logic, Controller handles HTTP. Easier to test each layer independently.

### Why Feature Dependencies Stay Acyclic?

Prevents circular dependencies (Auth → Orders → Auth) which cause initialization issues, make refactoring harder, and create hidden coupling between features.

### Why API Contracts Are Source of Truth?

Allows frontend and backend to develop in parallel. Frontend mocks against the contract; backend implements against the same contract. Integration happens when both are ready.

### Why Raw SQL, Not ORM?

Numbered SQL files give explicit schema versioning and reproducibility. Everyone can read the schema evolution. Easier to review schema changes in PRs.

---

## General Principles

**Feature-driven design:** Each feature (Auth, Orders, Holdings) is self-contained in its package/folder. New developers understand what belongs together.

**Layered responsibility:** Repository (data access), Service (business logic), Controller/Component (HTTP/UI) — each has one job.

**API contracts first:** Frontend and backend agree on the interface before either builds. Prevents surprises and integration hell.

**Secrets in environment files:** Never commit API keys, database passwords, or credentials. Use `.env`, `.env.local`, or environment variables. Add `.env*` to `.gitignore`.

**Mirror naming where it helps:** `auth` backend package ↔ `features/auth` frontend folder (makes relationships obvious). But don't force it if it doesn't make sense.

**Document non-obvious patterns:** Comments explain *why*, not *what*. Code is usually obvious; decisions and tradeoffs are not.

---

← Back to [AGENTS.md](../AGENTS.md)
