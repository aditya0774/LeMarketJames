# C7 – Role model

Internal views (audit, reports, insights) can enforce access now, without waiting for a staff admin feature.

## Where it's defined

| What | Code |
|---|---|
| The roles, and what each one is for | [Role.java](../libs/common/src/main/java/com/lemarketjames/common/security/Role.java) |
| Staff logins | the `staff_users` table ([010](../database/schema/010_shared_contracts.sql)), [StaffUserEntity.java](../libs/common/src/main/java/com/lemarketjames/common/domain/StaffUserEntity.java) |
| Who may log in at all | [AccountStatus.java](../libs/common/src/main/java/com/lemarketjames/common/domain/AccountStatus.java) for clients, the `active` flag for staff |

## How roles travel

1. **Login.** Clients and staff use the same endpoint (`POST /api/auth/login`, [C6](C6-api.md#auth-auth-service)). A client gets `CLIENT`; a staff user gets their one staff role. Nobody is both.
2. **Token.** The roles go into the JWT's `roles` claim ([JwtService.java](../libs/common/src/main/java/com/lemarketjames/common/security/JwtService.java)). A token issued before roles existed counts as `CLIENT`.
3. **Services.** Every service's `JwtAuthenticationFilter` turns the roles into `ROLE_<name>` authorities, so endpoints guard with `hasRole("...")` in their `SecurityConfig`. Always enforce roles on the server; the frontend only uses them to decide what to show.
4. **Frontend.** `/login` and `/me` return `roles`; [auth.ts](../apps/frontend/src/app/core/auth/auth.ts) keeps them in `Auth.roles()` / `Auth.hasRole()`. Staff have no trading account, so they get no `accountId`.

## Who may use what

| View or action | Roles | Status |
|---|---|---|
| Place orders; own dashboard, holdings, balance, profile, trades | `CLIENT` (own data only) | Enforced: needs the caller's own account |
| Move an order through its lifecycle, reject orders | `TRADING_OPS` (any client's orders) | Enforced in core-service `SecurityConfig` |
| Audit trail | `COMPLIANCE` | Planned ([C6](C6-api.md#planned-agreed-not-built)); guard with this role |
| Reports | `ANALYST`, `COMPLIANCE` | Planned; guard with these roles |
| Insights | `ANALYST` | Planned; guard with this role |

When a story adds an internal view, add its row here and the `hasRole` rule in the owning service's `SecurityConfig`.

## Mirrors (change together with `Role`)

- The `staff_users.role` CHECK constraint ([010](../database/schema/010_shared_contracts.sql)); it lists every role except `CLIENT`.
- The frontend `Role` type ([auth.ts](../apps/frontend/src/app/core/auth/auth.ts)).
