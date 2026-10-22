# Deploying to AWS (the class `leap-<team>` stack)

How LeMarketJames runs on the class AWS setup, and what each team's stack must provide. The class
material this follows is the "Amazon Setup" folder of
[FidelityLeapDailyReviews](https://github.com/bhickey777/FidelityLeapDailyReviews/tree/main/Day42-1020/Amazon%20Setup):
the AWS setup guides, `run-leap-sql.sh` and the example `Jenkinsfile.aws`. The instructor's
`leap-<team>` CloudFormation stack creates the AWS resources; this repository supplies what goes
into them, through [Jenkinsfile.aws](../../Jenkinsfile.aws). Every team deploys to **us-east-1**.

The local stack (Docker Compose) and the test pipeline ([Jenkinsfile](../../Jenkinsfile)) are unchanged.

## Contents

1. [How it fits together](#how-it-fits-together)
2. [What the stack must provide](#what-the-stack-must-provide)
3. [Task definition settings](#task-definition-settings)
4. [Load the database](#load-the-database)
5. [Run the deployment pipeline](#run-the-deployment-pipeline)
6. [Check the deployment](#check-the-deployment)
7. [What changes from the local stack](#what-changes-from-the-local-stack)
8. [Troubleshooting](#troubleshooting)

## How it fits together

```
Browser ──HTTPS──> CloudFront (main: trading app)  ─┬─ default (*)  ──> S3: apps/frontend/dist/lemarket-ui/browser
Browser ──HTTPS──> CloudFront (admin: staff app)   ─┼─ default (*)  ──> S3: apps/frontend/dist/staff/browser
                                                    └─ /api/*       ──> ALB ──> ECS Fargate services ──> RDS PostgreSQL
```

The class stack has four ECS services, four ECR repositories and two front ends. Ours map onto them:

| Class name | ECS service / ECR repository | This repository | Serves |
|---|---|---|---|
| orders | `leap-<team>-orders` | `services/orders-service` | `/api/v1/orders/**`, `/api/v1/buy-orders/**`, `/api/v1/audit/**` |
| auth | `leap-<team>-auth` | `services/auth-service` | `/api/auth/**`, `/api/v1/clients/*/status` |
| holdings | `leap-<team>-holdings` | `services/trading-service` | Every other `/api/**` path: market, quotes, instruments, holdings, portfolio, cash, watchlist, notifications |
| reporting | `leap-<team>-reporting` | `services/staff-ops-service` | `/api/v1/reports/**`, `/api/v1/surveillance/**` |
| main front end | `BucketName`, `DistributionId` | trading app (`ng build`) | Clients |
| admin front end | `AdminBucketName`, `AdminDistributionId` | staff app (`ng build staff`) | Trading Ops and Analysts |

Both apps call `/api` on their own origin (`apiBaseUrl` is empty), so CloudFront's `/api/*` behavior
is all they need. The table matches the local Nginx routing ([C6](../../contracts/C6-api.md#which-web-app-routes-which-paths)).

## What the stack must provide

Give this section to the instructor: these are settings of the `leap-<team>` stack, not of this repository.

### 1. Load balancer rules

The class guide's example rules (`/api/orders/*`, `/api/auth/*`, `/api/holdings/*`) belong to the
Hello World app. Ours, on the HTTP:80 listener, in this priority order:

| Priority | Path patterns | Forward to |
|---|---|---|
| 10 | `/api/auth/*`, `/api/v1/clients/*/status` | auth |
| 20 | `/api/v1/orders`, `/api/v1/orders/*`, `/api/v1/buy-orders`, `/api/v1/buy-orders/*`, `/api/v1/audit/*` | orders |
| 30 | `/api/v1/reports/*`, `/api/v1/surveillance/*` | reporting |
| 40 | `/api/*` | holdings |
| default | | fixed 404 |

ALB forwards the full path, which is what every service expects: no rewriting. An ALB `*` also matches `/`,
so `/api/*` covers everything under `/api`. `/internal/**` (service-to-service only) must never get a rule.

### 2. Target group health checks

`GET /health` on the container port, success code `200`, as in the class guide. Every service answers it
without a session ([LoadBalancerHealthController](../../libs/common/src/main/java/com/lemarketjames/common/health/LoadBalancerHealthController.java)):
the same result as `/actuator/health`, database included, `503` when it is down.

### 3. CloudFront, on both distributions

- **`/api/*` behavior** to the ALB origin: all HTTP methods, caching disabled (`CachingDisabled`), and an
  origin request policy that forwards all cookies, query strings and headers except `Host`
  (`AllViewerExceptHostHeader`). The session is an HttpOnly cookie, so cookies must reach the services.
- **Angular routes on the default (S3) behavior: use a CloudFront Function, not error pages.** The class guide's
  "SPA routing fallback" is often set up as custom error responses (403/404 → `/index.html`, 200). Those apply to
  the whole distribution, `/api/*` included, and would turn our API's 403 and 404 answers (wrong role, no account)
  into the Angular page. Instead, attach this function to the default behavior only, on viewer request:

  ```js
  function handler(event) {
    var request = event.request;
    // Angular routes have no file extension; the app's own files (main-*.js, assets) do.
    if (request.uri.indexOf('.') === -1) { request.uri = '/index.html'; }
    return request;
  }
  ```

- Live updates (`/api/v1/orders/stream`, `/api/v1/portfolio/stream`) are server-sent events. CloudFront closes a
  quiet connection after its origin response timeout (30 s by default; up to 60 s). The apps reconnect and catch up
  by themselves; raising the timeout on the `/api/*` origin makes it happen less often.

### 4. ECS services

- **holdings (trading-service) runs exactly one task.** It simulates the market in memory; two tasks would show
  two different prices.
- **The services call each other** directly, not through the ALB: orders → holdings for prices, sell checks and
  settlement (`/internal/**`, with a shared token), holdings → orders for trade history and buying power. Each needs
  the other's address inside the VPC, for example with ECS Service Connect or Cloud Map, and the tasks' security
  group must allow the container ports from itself. The addresses go into the task definitions (below).
- **Container ports.** The services listen on 8082 (auth), 8084 (holdings), 8085 (orders) and 8086 (reporting).
  If the stack uses other ports (the class guide uses 8090), set `SERVER_PORT` in the task definition to match.

### 5. Database

- PostgreSQL **14 or later** (the migrations use `CREATE OR REPLACE TRIGGER`).
- The services connect as **`lemarket_app`**, never as the master user `leapadmin`. They refuse to start on an
  account that could change audit records ([C2](../../contracts/C2-audit.md#the-lockdown)), and `leapadmin` owns
  the tables. `lemarket_app` is created when the schema is loaded ([below](#load-the-database)); its password
  belongs in Secrets Manager, for the task definitions.
- **Backups (BR-14):** RDS automated backups with a retention of at least 7 days, and deletion protection. They
  replace the `db-backup` container of the local stack.
- **Recording refused changes (BR-14):** a DB parameter group with `log_statement = ddl` and
  `log_line_prefix = '%m [%p] %quser=%u db=%d app=%a client=%h '`, the settings of the Compose `db` service, with
  the PostgreSQL log exported to CloudWatch Logs. Then every refused attempt to change an audit or trade record is
  logged with who tried.

### 6. Kafka (optional)

The order events go to Kafka when one is configured. Without a broker, orders-service logs them instead and
everything else works, but client notifications, Trading Ops' alerts (BR-17) and market activity stay empty.
If the class provides a broker, set the Kafka variables below; orders-service creates its topics itself.

## Task definition settings

The pipeline keeps each service's existing task definition and changes only its image, so these must be in
the task definitions. Secrets come from Secrets Manager. Defaults for everything else are in
[C5](../../contracts/C5-config.md) and each service's `application.properties`.

**All four services**

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<RDS endpoint>:5432/leap?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `lemarket_app` |
| `SPRING_DATASOURCE_PASSWORD` | secret: the `lemarket_app` password |
| `JWT_SECRET` (or `JWT_SHARED_SECRET`, the class name; either works) | secret: one value, at least 32 bytes, the same in all four |
| `SERVER_PORT` | only if the stack's container port differs from the service's own |

**Per service**

| Service | Variable | Value |
|---|---|---|
| auth | `AUTH_COOKIE_SECURE` | `true` (CloudFront is HTTPS) |
| auth | `LMJ_SESSION_INACTIVITYTIMEOUT` | optional; `PT1M` by default (BR-03) |
| orders | `INTERNAL_API_TOKEN` | secret: the same value as holdings |
| orders | `MARKET_SERVICE_URL`, `HOLDINGS_SERVICE_URL` | holdings' address in the VPC, e.g. `http://holdings:8084` |
| orders | `LMJ_EVENTS_PUBLISHER`, `KAFKA_BOOTSTRAP_SERVERS` | `kafka` and the broker address, only with Kafka |
| holdings | `INTERNAL_API_TOKEN` | secret: the same value as orders |
| holdings | `ORDERS_SERVICE_URL` | orders' address in the VPC, e.g. `http://orders:8085` |
| holdings, reporting | `LMJ_EVENTS_CONSUMER`, `KAFKA_BOOTSTRAP_SERVERS` | `kafka` and the broker address, only with Kafka |

**Never set in AWS:** `SIM_CONTROL_ENABLED`, `LMJ_SETTLEMENT_CONTROL_ENABLED`, `LMJ_EXECUTION_CONTROL_ENABLED`. They
switch on test controls that move prices and break settlement; Compose sets them for the end-to-end tests only.
`APP_CORS_ALLOWED_ORIGIN` is not needed: the browser calls the API on the app's own CloudFront domain.

## Load the database

Once, into the empty `leap` database, with the class script `run-leap-sql.sh` from a CloudShell VPC environment
(see the class guide "Loading SQL with CloudShell"). The files in [database/schema](../../database/schema) are
already named in run order; the script runs a directory's `*.sql` files in name order.

1. Choose a strong password for `lemarket_app` and store it in Secrets Manager for the task definitions.
2. In the CloudShell VPC environment, with this repository's `database/schema` folder and `run-leap-sql.sh`:

   ```bash
   export APP_DB_PASSWORD='<the lemarket_app password>'   # read by 016_audit_lockdown.sql
   ./run-leap-sql.sh <team> database/schema/
   ```

   Do not use `-t`: each file manages its own transactions. Without `APP_DB_PASSWORD`, migration 016 gives
   `lemarket_app` the development password.
3. Check, through the same script or `psql`:

   ```sql
   SELECT count(*) FROM instruments;                      -- 51
   SELECT rolname FROM pg_roles WHERE rolname = 'lemarket_app';
   SELECT has_table_privilege('lemarket_app', 'audit_log', 'UPDATE');   -- false
   ```

- The load includes the seed data set ([C3](../../contracts/C3-seed-data.md)): the demo clients and staff logins,
  all with the password `Pass123!`. Fine for a class demo; remove or change them for anything real.
- **Later schema changes:** run only the new numbered files. 001–003 are not safe to run twice; the rest are.

## Run the deployment pipeline

On the class Jenkins: a Pipeline job on this repository with script path `Jenkinsfile.aws`, run with the
`TEAM_ID` parameter. Run the test pipeline (`Jenkinsfile`) on the same commit first: this one runs no tests.

| Stage | Does |
|---|---|
| Validate AWS credentials and stack | Assumes `leap-<team>-deploy`; checks the stack and the ECS cluster exist |
| Build Docker images | The four backend images, from the repo root (the Dockerfiles build with Maven there), for `linux/amd64` |
| Build Angular applications | `ng build` (trading app) and `ng build staff` (staff app) in `apps/frontend` |
| Publish images and Angular files | Pushes to ECR, syncs the two S3 buckets, invalidates both distributions |
| Instructor approval | Waits for an instructor before any ECS service changes |
| Deploy images to existing ECS services | New task definition revision with the new image only; waits for all four to be stable |
| Backend health checks | Waits until every target group reports its service healthy (`GET /health`) |
| Route checks | Through both CloudFront domains: `/api/auth/me` 401, `/api/v1/orders/trades/search` 401, `/api/market/quotes` 200, `/api/v1/reports/ping` 401, each a JSON answer |

A route check's 401 shows the request reached a service and was refused for want of a session; the JSON
requirement catches CloudFront answering an API path with the Angular page.

## Check the deployment

After a green pipeline, by hand, on the two CloudFront domains:

1. **Trading app:** sign in as `seed_trader@seed.lemarket.com` (`Pass123!`); the Overview shows holdings and live prices.
2. Place a small BUY. With the market open (US hours) it fills; outside them it waits as DELAYED, as designed.
3. Reload a deep link such as `/dashboard`: the app opens (the CloudFront Function at work).
4. **Staff app:** sign in as `ops@seed.lemarket.com`; trade search finds the order and its timeline.
5. Sign in as `analyst@seed.lemarket.com`; the dashboard tiles load.
6. Leave a page idle: the inactivity warning appears and the session ends (BR-03).

## What changes from the local stack

| Local (Docker Compose) | AWS |
|---|---|
| Nginx serves each app and routes `/api` | S3 + CloudFront serve the apps; the ALB routes `/api` |
| HTTPS with a self-signed certificate | CloudFront's certificate; CloudFront to the ALB is HTTP in the class setup (the class guide's own caveat) |
| The staff app's Nginx renames the session cookie to `staff_jwt` and exposes only staff paths | Not needed: the two apps have different domains, so their cookies are separate. Every staff path is still guarded by role in the services |
| `db-backup` container, nightly `pg_dump` | RDS automated backups |
| Kafka in Compose | Only if the class provides a broker |
| Services reach each other by Compose service name | By the VPC addresses in the task definitions |

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| A service's task stops at start, log says the account may change audit records | `SPRING_DATASOURCE_USERNAME` is `leapadmin`; use `lemarket_app` |
| Task stops at start with a schema validation error | A migration was not loaded; run the missing numbered files |
| Targets unhealthy | Health check path not `/health`, port not the container port, or the tasks' security group does not allow the ALB |
| API calls return the Angular page | CloudFront custom error responses; use the function above |
| `404` on API calls | ALB rules still the Hello World paths |
| Sign-in works, the next call is `401` | CloudFront not forwarding cookies on `/api/*` |
| Orders stay PENDING | orders cannot reach holdings: `MARKET_SERVICE_URL`/`HOLDINGS_SERVICE_URL`, `INTERNAL_API_TOKEN`, or the security group between tasks |
| Prices differ between page loads | holdings runs more than one task |
