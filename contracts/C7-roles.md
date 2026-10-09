# C7 – Role model

Internal views (audit, reports, insights) can enforce access now, without waiting for a staff admin feature.

## Where it's defined

| What | Code |
|---|---|
| The roles, and what each one is for | [Role.java](../libs/common/src/main/java/com/lemarketjames/common/security/Role.java) |
| Staff logins | the `staff_users` table ([010](../database/schema/010_shared_contracts.sql)), [StaffUserEntity.java](../libs/common/src/main/java/com/lemarketjames/common/domain/StaffUserEntity.java) |
| Who may log in at all | [AccountStatus.java](../libs/common/src/main/java/com/lemarketjames/common/domain/AccountStatus.java) for clients, the `active` flag for staff |

## How roles travel

1. **Login.** Clients and staff use the same endpoint (`POST /api/auth/login`, [C6](C6-api.md#auth-auth-service)), each through their own gateway. A client gets `CLIENT`; a staff user gets their one staff role. Nobody is both.
2. **Token.** The roles go into the JWT's `roles` claim ([JwtService.java](../libs/common/src/main/java/com/lemarketjames/common/security/JwtService.java)). A token issued before roles existed counts as `CLIENT`.
3. **Services.** Every service's `JwtAuthenticationFilter` turns the roles into `ROLE_<name>` authorities, so endpoints guard with `hasRole("...")` in their `SecurityConfig`. Always enforce roles on the server; the frontend only uses them to decide what to show.
4. **Frontend.** `/login` and `/me` return `roles`; [auth.ts](../apps/frontend/src/app/core/auth/auth.ts) keeps them in `Auth.roles()` / `Auth.hasRole()`. Staff have no trading account, so they get no `accountId`. The staff app keeps them the same way in its own [auth.ts](../apps/frontend/projects/staff/src/app/core/auth/auth.ts) ([The staff app](#the-staff-app)).

## The staff session cookie

A browser shares cookies between the ports of one host. The trading app and the staff app are on the same host, so if both kept their session in a cookie named `jwt`, signing in to one would sign the other out.

| | Trading app | Staff app |
|---|---|---|
| Cookie the browser holds | `jwt` | `staff_jwt` |
| Cookie the services set and read | `jwt` | `jwt` |
| Set and cleared through | the trading gateway | the staff gateway |

- **One place knows the staff name:** the staff gateway's [StaffSessionCookieGatewayFilterFactory.java](../services/gateway-service/src/main/java/com/lemarketjames/gateway/StaffSessionCookieGatewayFilterFactory.java). On a response it renames `Set-Cookie: jwt` to `staff_jwt`, keeping the attributes auth-service chose (HTTP-only, `SameSite=Lax`, path `/`, lifetime). On a request it renames `staff_jwt` to `jwt`. No service, and no `JwtAuthenticationFilter`, changes.
- **A customer session is not a staff session.** The browser sends the customer's `jwt` to the staff app too. The staff gateway drops it before renaming, so only a sign-in through the staff gateway counts there. The other way round, the trading gateway passes `staff_jwt` on untouched and no service reads it.
- **Signing out** of one app clears only that app's cookie.
- The cookie only separates the two sessions; it grants nothing. Roles are still checked by each service, so a `CLIENT` who signs in through the staff gateway holds a `staff_jwt` that every staff route refuses with `403`.

## Who may use what

| View or action | Roles | Status |
|---|---|---|
| Place orders; own dashboard, holdings, balance, profile, trades | `CLIENT` (own data only) | Enforced: needs the caller's own account |
| Move an order through its lifecycle, reject orders | `TRADING_OPS` (any client's orders) | Enforced in buy-sell-service `SecurityConfig` |
| Search trades by order ID or client and date range | `TRADING_OPS` (any client's filled orders) | Enforced in buy-sell-service `SecurityConfig`; [C6](C6-api.md#trade-search-trading_ops-only) |
| Audit trail | `TRADING_OPS` | Planned ([C6](C6-api.md#planned-agreed-not-built)); guard with this role |
| Reports (`/api/v1/reports/**`) | `ANALYST` | Enforced in reporting-service `SecurityConfig`; every other role gets `403` ([C6](C6-api.md#reports-reporting-service)) |
| Insights | `ANALYST` | Planned; guard with this role |
| Notifications about one's own orders (`/api/v1/notifications/**`) | `CLIENT` (own account only) | Enforced in notification-service `SecurityConfig`; staff get `403` ([C6](C6-api.md#get-apiv1notifications-client-only)) |
| Large-order alerts (`/api/v1/surveillance/**`) | `TRADING_OPS` | Enforced in surveillance-service `SecurityConfig`; every other role gets `403` ([C6](C6-api.md#get-apiv1surveillancealerts-trading_ops-only)) |
| Market activity (`/api/v1/market-activity/**`) | every signed-in role | Enforced in activity-service `SecurityConfig`: signed in, any role. It holds aggregates only ([C6](C6-api.md#get-apiv1market-activity)) |

When a story adds an internal view, add its row here and the `hasRole` rule in the owning service's `SecurityConfig`.

The frontend trade-search route also checks `TRADING_OPS` with `tradingOpsGuard`; other signed-in
roles see Access denied, and only operations see the search navigation link. This UI check
supplements the owning service's authorization.

## The staff app

The staff app opens on its own login page, which signs in through the staff gateway.

| Who signs in | What happens |
|---|---|
| A staff role with a section | Lands on that section's start page: trade search for `TRADING_OPS`, the Analyst dashboard for `ANALYST`. Where each role lands is one list, [staff-home.ts](../apps/frontend/projects/staff/src/app/core/auth/staff-home.ts) |
| `CLIENT` | Signed straight out again, with "This login is for staff only". The gateway cannot refuse the sign-in itself, because it checks no role |

Pages are guarded with `requiresRole(...roles)` ([role.guard.ts](../apps/frontend/projects/staff/src/app/core/auth/role.guard.ts)), which takes the roles that may open a page:

- a signed-out visitor is sent to the staff login
- a signed-in account with none of the roles sees Access denied.

As in the trading app, this decides what is shown and nothing more: the service behind a page enforces the role itself.

## Mirrors (change together with `Role`)

- The `staff_users.role` CHECK constraint ([010](../database/schema/010_shared_contracts.sql)); it lists every role except `CLIENT`.
- The frontend `Role` type, once per app because the two share no code: the trading app's [auth.ts](../apps/frontend/src/app/core/auth/auth.ts) and the staff app's [auth.ts](../apps/frontend/projects/staff/src/app/core/auth/auth.ts).

One more mirror, of the cookie name rather than of `Role`: `SERVICE_COOKIE` in the staff gateway's `StaffSessionCookieGatewayFilterFactory` repeats `JwtAuthenticationFilter.COOKIE_NAME`, because the gateway is reactive and cannot depend on `libs/common`. Change them together.
