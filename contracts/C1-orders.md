# C1 – Order model and status lifecycle

Every order story codes against the same order shape and lifecycle, so validation, execution and history can be built in parallel.

## Where it's defined

| What | Code |
|---|---|
| Order fields | [Order.java](../services/core-service/src/main/java/com/lemarketjames/orders/entity/Order.java); the API shape is the order DTO in [C6](C6-api.md#orders-core-service) |
| Statuses, what each means, and the allowed next statuses | `Order.OrderStatus`, including `allowedNext()` |
| Rejection reason codes | [RejectionReason.java](../services/core-service/src/main/java/com/lemarketjames/orders/entity/RejectionReason.java) |

## Rules

- **A new order is `SUBMITTED`**, and only once every placement check has passed. A refused placement saves nothing. It answers with a `RejectionReason` code instead ([C6](C6-api.md#post-apiv1orders)).
- **Every status change goes through `Order.transitionTo`** (or `Order.reject`, which records a reason code). A move the lifecycle doesn't allow throws `InvalidStatusTransitionException`, and the API answers `409 INVALID_STATUS_TRANSITION`. `setOrderStatus` exists only to put fixtures into a known state.
- **`FILLED` and `REJECTED` are final.** `OrderStatus.isOpen()` tells open from final; use it rather than your own list.
- **`rejection_reason` holds a `RejectionReason` code**, never free text.
- **Filling settles first.** Moving to `FILLED` needs a price and settles cash and holdings through holdings-service before the status is saved, so an order is never FILLED without its side effects.
- **Every transition is audited and announced.** It writes its audit event in the same transaction ([C2](C2-audit.md)) and publishes `OrderStatusChanged`, plus `OrderFilled` on a fill ([C6](C6-api.md#internal-events-and-the-execution-interface-core-service)).

## Who moves orders

- Trading operations staff, through the status and reject endpoints (role `TRADING_OPS`, [C7](C7-roles.md)).
- The execution engine, through `OrderExecutor` ([C6](C6-api.md#internal-events-and-the-execution-interface-core-service)). It returns a result, and its caller applies it with `transitionTo`.
- Clients only place orders. They can't change an order's status, not even on their own orders.

## Mirrors (change together with `OrderStatus`)

- The `orders.order_status` CHECK constraint ([001](../database/schema/001_core_schema.sql)).
- The frontend `OrderStatus` type ([order.service.ts](../apps/frontend/src/app/core/orders/order.service.ts)) and its open-status list ([order-status.ts](../apps/frontend/src/app/features/dashboard/orders-panel/order-status.ts)).
- holdings-service's open-status list ([OrderSummary.java](../services/holdings-service/src/main/java/com/lemarketjames/orders/client/OrderSummary.java)).
