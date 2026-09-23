# libs/common

Shared Java library used by every backend microservice (`services/*`). It's a plain jar, not a runnable app.

| Package | Contents |
|---|---|
| `com.lemarketjames.common.security` | `JwtService` (issue/validate tokens) and `JwtAuthenticationFilter` (reads the `jwt` cookie). Every service must use the same `JWT_SECRET`. |
| `com.lemarketjames.common.domain` | JPA entities/repositories for `clients`, `addresses`, `accounts` in the shared database. |
| `com.lemarketjames.common.error` | `GlobalExceptionHandler` and `ValidationException`, so all services return one error format. |

Only put code here that more than one service needs. Feature-specific code stays in its service.
Services pick these beans up automatically because their `@SpringBootApplication` class lives in the
root `com.lemarketjames` package.
