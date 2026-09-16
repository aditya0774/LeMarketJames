# Frontend Architecture

Angular SPA at `apps/frontend/src/app`. Three-folder model: core/, shared/, features/. Services return Observables; components subscribe.

## Directory Structure

**`core/`** — App-wide singletons (provided at root level once)
- Authentication state, HTTP interceptors, guards
- Injectable data-access services (OrdersService, HoldingsService, QuotesService)
- `core/auth/`, `core/orders/`, `core/quotes/`, `core/holdings/`, `core/interceptors/`
- Injected into every component that needs them (dependency injection via constructor)
- Imported once at root; never re-created per-feature

**`shared/`** — Reusable, presentational code
- Components, pipes, directives, models used by multiple features
- Must not depend on a specific feature or hold app-wide state
- Examples: `shared/models/`, `shared/components/`, `shared/pipes/`

**`features/<feature-name>/<component-name>/`** — Routed, feature-specific UI
- Examples: `features/auth/login/`, `features/auth/register/`, `features/orders/order-list/`
- Inject core services and use shared components
- Do not import directly from other features

## Service Patterns

**Observables, not Promises**
- Core services return RxJS Observables, not Promises
- Components subscribe and handle data asynchronously
- Unsubscribe using async pipe in templates or takeUntil in components to avoid memory leaks

**Dependency Injection via Constructor**
```typescript
constructor(private orderService: OrdersService) {}

ngOnInit() {
  this.orderService.getOrders().subscribe(orders => {
    this.orders = orders;
  });
}
```

**Singleton Services**
- Services are provided at root level with `providedIn: 'root'` in @Injectable decorator
- Spring creates one instance; all components share it
- No need to import the service module multiple times

## What NOT to Do

- ❌ Don't import from `core/` unless accessing global state (auth, interceptors, guards)
- ❌ Don't make `core/` services location-aware or feature-specific
- ❌ Don't hold feature-specific state in core services
- ❌ Don't forget to unsubscribe from Observables (use async pipe or takeUntil)

---

← Back to [AGENTS.md](../AGENTS.md)
