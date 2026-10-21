# C7 – Role model

Internal views (audit, reports, insights) can enforce access now, without waiting for a staff admin feature.

## Where it's defined

| What | Code |
|---|---|
| The roles, and what each one is for | [Role.java](../libs/common/src/main/java/com/lemarketjames/common/security/Role.java) |
| Staff logins | the `staff_users` table ([010](../database/schema/010_shared_contracts.sql)), [StaffUserEntity.java](../libs/common/src/main/java/com/lemarketjames/common/domain/StaffUserEntity.java) |
| Who may log in at all | [AccountStatus.java](../libs/common/src/main/java/com/lemarketjames/common/domain/AccountStatus.java) for clients, the `active` flag for staff. Enforced at sign-in and again on every request by [JpaAccountAccessCheck](../libs/common/src/main/java/com/lemarketjames/common/security/JpaAccountAccessCheck.java), so an open session ends as soon as the login loses access ([C6](C6-api.md#rules-for-every-public-endpoint)). A client's status is changed only by Trading Ops or by the client closing their own account, each with a row in `account_status_changes` ([027](../database/README.md#027--account-status-history)) |

## How roles travel

1. **Login.** Clients and staff use the same endpoint (`POST /api/auth/login`, [C6](C6-api.md#auth-auth-service)), each through their own web app. A client gets `CLIENT`; a staff user gets their one staff role. Nobody is both.
2. **Token.** The roles go into the JWT's `roles` claim ([JwtService.java](../libs/common/src/main/java/com/lemarketjames/common/security/JwtService.java)). A token issued before roles existed counts as `CLIENT`.
3. **Services.** Every service's `JwtAuthenticationFilter` turns the roles into `ROLE_<name>` authorities, so endpoints guard with `hasRole("...")` in their `SecurityConfig`. Always enforce roles on the server; the frontend only uses them to decide what to show.
4. **Frontend.** `/login` and `/me` return `roles`; [auth.ts](../apps/frontend/src/app/core/auth/auth.ts) keeps them in `Auth.roles()` / `Auth.hasRole()`. Staff have no trading account, so they get no `accountId`. The staff app keeps them the same way in its own [auth.ts](../apps/frontend/projects/staff/src/app/core/auth/auth.ts) ([The staff app](#the-staff-app)).

## The staff session cookie

A browser shares cookies between the ports of one host. The trading app and the staff app are on the same host, so if both kept their session in a cookie named `jwt`, signing in to one would sign the other out.

| | Trading app | Staff app |
|---|---|---|
| Cookie the browser holds | `jwt` | `staff_jwt` |
| Cookie the services set and read | `jwt` | `jwt` |
| Set and cleared through | the trading app | the staff app |

- **Only the staff app knows the staff name:** its [nginx.conf](../apps/frontend/projects/staff/nginx.conf) in Docker and its dev-server proxy, [staff-proxy.mjs](../apps/frontend/projects/staff/staff-proxy.mjs), under `ng serve`; the two do the same thing. On a sign-in, sign-out or session refresh (`POST /api/auth/refresh`, BR-03) response it renames `Set-Cookie: jwt` to `staff_jwt`, keeping the attributes auth-service chose (HTTP-only, `SameSite=Lax`, path `/`, lifetime). Refresh is what keeps an active staff session going past the inactivity timeout ([C6](C6-api.md#auth-auth-service)), so without its route every staff session would end after a minute. On a request it sends `staff_jwt` to the service as `jwt`, and no other cookie. No service, and no `JwtAuthenticationFilter`, changes.
- **A customer session is not a staff session.** The browser sends the customer's `jwt` to the staff app too. The staff app drops it, so only a sign-in through the staff app counts there. The other way round, the trading app passes `staff_jwt` on untouched and no service reads it.
- **Signing out** of one app clears only that app's cookie.
- The cookie only separates the two sessions; it grants nothing. Roles are still checked by each service, so a `CLIENT` who signs in through the staff app holds a `staff_jwt` that every staff route refuses with `403`.

## Who may use what

| View or action | Roles | Status |
|---|---|---|
| Place orders; own dashboard, holdings, balance, profile, trades | `CLIENT` (own data only) | Enforced: needs the caller's own account |
| Move an order through its lifecycle, reject orders | `TRADING_OPS` (any client's orders) | Enforced in orders-service `SecurityConfig` |
| Search trades by order ID or client and date range | `TRADING_OPS` (any client's filled orders) | Enforced in orders-service `SecurityConfig`; [C6](C6-api.md#trade-search-trading_ops-only) |
| See and change a client's account status (suspend, reinstate, close, expire), with a reason and its history | `TRADING_OPS` (any client) | Enforced in auth-service `SecurityConfig`; [C6](C6-api.md#account-status-trading_ops-only) |
| Close one's own account | `CLIENT` (own account only, once no stocks or open orders remain) | Enforced in auth-service `SecurityConfig`; [C6](C6-api.md#closing-an-account-client-only) |
| Audit trail | `TRADING_OPS` | Planned ([C6](C6-api.md#planned-agreed-not-built)); guard with this role |
| Reports (`/api/v1/reports/**`) | `ANALYST` | Enforced in staff-ops-service `SecurityConfig`; every other role gets `403` ([C6](C6-api.md#reports-staff-ops-service)) |
| Insights (`/api/v1/reports/insights/**`) | `ANALYST` | Enforced by the same staff-ops-service rule as reports; every other role gets `403` ([C6](C6-api.md#get-apiv1reportsinsightsvolume), [active clients](C6-api.md#get-apiv1reportsinsightsactive-clients)) |
| Notifications about one's own orders (`/api/v1/notifications/**`) | `CLIENT` (own account only) | Enforced in trading-service `SecurityConfig`; staff get `403` ([C6](C6-api.md#get-apiv1notifications-client-only)) |
| Surveillance alerts: large orders and refused settlements (`/api/v1/surveillance/**`), shown on the staff app's Alerts page (`/alerts`) | `TRADING_OPS` | Enforced in staff-ops-service `SecurityConfig`; every other role gets `403` ([C6](C6-api.md#get-apiv1surveillancealerts-trading_ops-only)). The page's route is guarded with `requiresRole('TRADING_OPS')` too |
| Market activity (`/api/v1/market-activity/**`) | every signed-in role | Enforced in trading-service `SecurityConfig`: signed in, any role. It holds aggregates only ([C6](C6-api.md#get-apiv1market-activity)) |

When a story adds an internal view, add its row here and the `hasRole` rule in the owning service's `SecurityConfig`.

The staff app's Trading Ops pages, trade search (`/trade-search`) and Alerts (`/alerts`), also check
`TRADING_OPS` with `requiresRole('TRADING_OPS')`; other signed-in roles see Access denied. Only those
two pages show the Trading Ops tab bar linking them ([trading-ops-nav.ts](../apps/frontend/projects/staff/src/app/features/trading-ops/trading-ops-nav.ts)),
so no other role sees the links. This UI check supplements the owning service's authorization.

## The staff app

The staff app opens on its own login page, which signs in through the staff app's own origin.

| Who signs in | What happens |
|---|---|
| A staff role with a section | Lands on that section's start page: trade search for `TRADING_OPS`, the Analyst dashboard for `ANALYST`. Where each role lands is one list, [staff-home.ts](../apps/frontend/projects/staff/src/app/core/auth/staff-home.ts) |
| `CLIENT` | Signed straight out again, with "This login is for staff only". The staff app's proxy cannot refuse the sign-in itself, because it checks no role |

Pages are guarded with `requiresRole(...roles)` ([role.guard.ts](../apps/frontend/projects/staff/src/app/core/auth/role.guard.ts)), which takes the roles that may open a page:

- a signed-out visitor is sent to the staff login
- a signed-in account with none of the roles sees Access denied.

As in the trading app, this decides what is shown and nothing more: the service behind a page enforces the role itself.

Employee information (`/employee-information`, LMKT-144) has no role guard: every signed-in staff member opens it from the account menu and sees only their own details, from their session ([C6](C6-api.md#auth-auth-service)). It is read-only; staff logins are made by engineers.

## Mirrors (change together with `Role`)

- The `staff_users.role` CHECK constraint ([010](../database/schema/010_shared_contracts.sql)); it lists every role except `CLIENT`.
- The frontend `Role` type, once per app because each app keeps its own auth code (they share only UI building blocks, `projects/shared-ui`): the trading app's [auth.ts](../apps/frontend/src/app/core/auth/auth.ts) and the staff app's [auth.ts](../apps/frontend/projects/staff/src/app/core/auth/auth.ts).

Two more mirrors, not of `Role`:

- The staff app's `AccountStatus` type ([client-status.service.ts](../apps/frontend/projects/staff/src/app/core/clients/client-status.service.ts)) repeats [AccountStatus](../libs/common/src/main/java/com/lemarketjames/common/domain/AccountStatus.java) for its status panel, as do the CHECK constraints on `clients.account_status` (010) and `account_status_changes` (027). Change them together.
- The staff app's `ALERT_REASONS` ([order-alert.model.ts](../apps/frontend/projects/staff/src/app/shared/models/order-alert.model.ts)) repeats [AlertReason](../services/staff-ops-service/src/main/java/com/lemarketjames/surveillance/AlertReason.java) for the Alerts page's reason filter and labels. Change them together.
- The cookie name: the staff app's [nginx.conf](../apps/frontend/projects/staff/nginx.conf) and `SERVICE_COOKIE` in [staff-proxy.mjs](../apps/frontend/projects/staff/staff-proxy.mjs) repeat `JwtAuthenticationFilter.COOKIE_NAME` (`jwt`), because neither can read Java. Change them together.
