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

## API Contracts

**🔗 Reference:** `/API-CONTRACTS.md` is the single source of truth for all API endpoints, request/response formats, and team agreements.

All 6 developers work in parallel by following this contract strictly. Breaking changes require team approval.

### Backend: Implementing Against the Contract

When implementing a new endpoint (e.g., `POST /api/orders`):

1. **Consult `/API-CONTRACTS.md`** — Find your endpoint section
2. **Note the request structure** — Exact JSON fields and types
3. **Note the response structure** — All fields must be present
4. **Note the status codes** — Use the documented codes (201 for POST, 200 for GET, 404 for not found, etc.)
5. **Create the feature package:**
   ```
   com.lemarketjames.<feature>/
   ├── <Feature>Controller.java    (@RestController matching endpoint path)
   ├── <Feature>Service.java       (@Service with business logic)
   ├── dto/
   │   ├── <Feature>Request.java   (matches request JSON structure)
   │   └── <Feature>Response.java  (matches response JSON structure)
   └── <Feature>Repository.java    (if needed, queries the database)
   ```

6. **Return response JSON matching the contract exactly:**
   - Every field in the contract must be present
   - Field names must match exactly (case-sensitive)
   - Data types must match
   - Include all documented error cases (400, 401, 404, etc.)

**Important:** Don't add extra fields or change field names without team approval. The frontend is mocking against this contract and will break if you deviate.

### Frontend: Mocking & Building Against the Contract

When building UI for a feature (e.g., Orders Dashboard):

1. **Consult `/API-CONTRACTS.md`** — Find your endpoint section
2. **Create the service:**
   ```typescript
   // src/app/core/<feature>/<feature>.service.ts
   @Injectable({ providedIn: 'root' })
   export class OrdersService {
     constructor(private http: HttpClient) {}
     getOrders() { return this.http.get<OrdersResponse>('/api/orders'); }
     getOrder(id: string) { return this.http.get<OrderResponse>(`/api/orders/${id}`); }
     createOrder(req: CreateOrderRequest) { return this.http.post<OrderResponse>('/api/orders', req); }
   }
   ```

3. **Create TypeScript interfaces** matching the contract exactly:
   ```typescript
   // src/app/shared/models/<feature>.model.ts
   export interface Order {
     orderId: string;        // Match contract exactly
     symbol: string;
     quantity: number;
     type: 'BUY' | 'SELL';
     // ... all fields from the contract
   }
   ```

4. **Mock the service** using contract examples (don't wait for backend):
   ```typescript
   // During development, provide the mock
   { provide: OrdersService, useClass: OrdersServiceMock }
   // Build UI against this mock data
   ```

5. **When backend is ready,** swap the mock for the real service (same interface → seamless):
   ```typescript
   // Remove mock provider, use real OrdersService
   // Integration should work without UI changes
   ```

**Development Workflow:**
- Phase 1 (Parallel): Frontend builds UI with mocks, Backend implements endpoints
- Phase 2 (Integration): Swap mock for real service, test together

### Change Management

If you need to change the contract (endpoint path, request fields, response fields):
1. **Don't make the change unilaterally** — it breaks the parallel workflow
2. **Propose to the team** — discuss in standup or PR
3. **Update `/API-CONTRACTS.md` together** — it's the source of truth
4. **Update both backend and frontend** — keep them in sync
5. **Version the change** — document in the contract's version history

## General

- Keep files small and single-responsibility — a class/component should do one thing.
- Mirror naming across layers where it helps (e.g. `auth` package ↔ `features/auth` folder), but don't force a 1:1 mapping where it doesn't make sense.
- Always document and comment while developing for better readability 
- Develop with security as a priority
