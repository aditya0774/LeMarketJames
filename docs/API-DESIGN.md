# API Design & Contracts

All endpoints use `/api/v1/` prefix. Single source of truth is `/API-CONTRACTS.md`.

## API Versioning

All endpoints use the `/api/v1/` prefix for future compatibility:

- Endpoint format: `/api/v1/<resource>` (e.g., `/api/v1/orders`, `/api/v1/auth/login`, `/api/v1/holdings`)
- Version is always explicit in the path
- When implementing a new endpoint in backend:
  ```java
  @RestController
  @RequestMapping("/api/v1/orders")
  public class OrdersController { ... }
  ```
- When calling from frontend:
  ```typescript
  return this.http.get<OrderResponse>('/api/v1/orders/' + id);
  ```
- Document all endpoints in `/API-CONTRACTS.md` with the `/api/v1/` prefix

## API Contracts

**🔗 Reference:** `/API-CONTRACTS.md` is the single source of truth for all API endpoints, request/response formats, and team agreements.

### Backend: Implementing Against the Contract

1. **Consult `/API-CONTRACTS.md`** — Find your endpoint section
2. **Match the contract exactly:**
   - Every field in the contract must be present in response
   - Field names must match exactly (case-sensitive)
   - Data types must match
   - Include all documented error cases (400, 401, 404, etc.)
3. **Don't add extra fields or change field names** without team approval — the frontend mocks against this contract

### Frontend: Building Against the Contract

1. **Consult `/API-CONTRACTS.md`** — Find your endpoint section
2. **Create TypeScript interfaces** matching the contract exactly
3. **Create services** calling `/api/v1/` endpoints
4. **Mock the service** during development (don't wait for backend)
5. **When backend is ready,** swap mock for real service (same interface → seamless integration)

### Change Management

If you need to change the contract (endpoint path, request fields, response fields):

1. **Don't make the change unilaterally** — it breaks the parallel workflow
2. **Propose to the team** — discuss in standup or PR
3. **Update `/API-CONTRACTS.md` together** — it's the source of truth
4. **Update both backend and frontend** — keep them in sync
5. **Version the change** — document in the contract's version history

---

← Back to [AGENTS.md](../AGENTS.md)
