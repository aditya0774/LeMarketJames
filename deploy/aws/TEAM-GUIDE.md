# Team guide: deploying to AWS, and fixing what goes wrong

A step-by-step walkthrough for deploying LeMarketJames to our team's class stack, followed by the problems you are
most likely to hit and how to fix each one. The reference tables (load balancer rules, task definition variables,
CloudFront settings) live in [README.md](README.md) next to this file; this guide links to them instead of
repeating them.

In every command below, replace `team3` with your own team. Every team deploys to **us-east-1**.

## Contents

1. [The deployment in one picture](#the-deployment-in-one-picture)
2. [Before you start](#before-you-start)
3. [Step 1: create the database password](#step-1-create-the-database-password)
4. [Step 2: load the database](#step-2-load-the-database)
5. [Step 3: check the task definitions](#step-3-check-the-task-definitions)
6. [Step 4: run the pipeline](#step-4-run-the-pipeline)
7. [Step 5: check it by hand](#step-5-check-it-by-hand)
8. [Where to look when something fails](#where-to-look-when-something-fails)
9. [Hiccups and fixes](#hiccups-and-fixes)
    - [Loading the database](#loading-the-database)
    - [ECS tasks that stop or restart](#ecs-tasks-that-stop-or-restart)
    - [Load balancer and health checks](#load-balancer-and-health-checks)
    - [CloudFront and the web apps](#cloudfront-and-the-web-apps)
    - [The pipeline](#the-pipeline)
    - [The app runs but something looks wrong](#the-app-runs-but-something-looks-wrong)

## The deployment in one picture

```
Browser ─HTTPS─> CloudFront (main: trading app) ─┬─ everything else ─> S3 bucket (the Angular files)
Browser ─HTTPS─> CloudFront (admin: staff app)  ─┤
                                                 └─ /api/*  ─> load balancer ─> 4 ECS services ─> RDS PostgreSQL
```

The class stack names its services orders, auth, holdings and reporting. **holdings is our trading-service** and
**reporting is our staff-ops-service**; orders and auth are what they say. The full mapping is in
[README.md](README.md#how-it-fits-together).

## Before you start

- [ ] The commit you deploy passed the regular `Jenkinsfile` (the test pipeline). `Jenkinsfile.aws` runs no tests.
- [ ] You can sign in to the class AWS console and open CloudShell in us-east-1.
- [ ] You can run the deployment job on the class Jenkins.
- [ ] The instructor has confirmed the stack settings in [What the stack must provide](README.md#what-the-stack-must-provide).
      The two that break everything when missed: the **load balancer rules for our paths** and the services logging in as
      **`lemarket_app`**.

## Step 1: create the database password

The services never log in to the database as the master user `leapadmin`. They use a restricted login,
`lemarket_app`, which can add audit records but never change them. You choose its password once, keep it in Secrets
Manager, and use it twice: when loading the database (Step 2) and in the task definitions (Step 3).

In CloudShell (a normal session is fine for this step):

```bash
TEAM=team3
aws secretsmanager create-secret --region us-east-1 \
  --name "leap-$TEAM/lemarket-app-password" \
  --secret-string "$(aws secretsmanager get-random-password --region us-east-1 \
      --password-length 32 --exclude-punctuation --query RandomPassword --output text)"
```

Letters and digits only, so it never needs quoting. If your AWS user may not create secrets, ask the instructor to
create one under that name.

## Step 2: load the database

The database is private: you load it from a **CloudShell VPC environment** inside our stack's network, with the
class script `run-leap-sql.sh` (in the class repo's "AWS Scripts" folder).

1. **Create the CloudShell VPC environment** (CloudShell, then the environment menu, then Create VPC environment):
   region us-east-1, the `leap-team3` VPC, one of its **private** subnets, and the stack's **TasksSecurityGroup**. The
   database only accepts that security group.
2. **Get the files into it.** A VPC environment cannot upload files, but it reaches the internet through the
   stack's NAT gateway, so clone our repo and the class repo (for the script):
   ```bash
   git clone https://github.com/aditya0774/LeMarketJames.git
   git clone --depth 1 https://github.com/bhickey777/FidelityLeapDailyReviews.git
   cp "FidelityLeapDailyReviews/Day42-1020/Amazon Setup/AWS Scripts/run-leap-sql.sh" LeMarketJames/
   cd LeMarketJames
   chmod +x run-leap-sql.sh
   ```
   Deploy the branch that passed the tests: add `git checkout <branch>` if it isn't `main`.
3. **Set the password and load every migration, in order:**
   ```bash
   TEAM=team3
   export APP_DB_PASSWORD="$(aws secretsmanager get-secret-value --region us-east-1 \
       --secret-id "leap-$TEAM/lemarket-app-password" --query SecretString --output text)"
   ./run-leap-sql.sh "$TEAM" database/schema/
   unset APP_DB_PASSWORD
   ```
   Check the confirmation screen says your team before typing `y`. Don't add `-t`: each file runs its own
   transactions. Migration 016 reads `APP_DB_PASSWORD` when it creates `lemarket_app`.
4. **Check it worked:**
   ```bash
   cat > check.sql <<'SQL'
   SELECT count(*) AS instruments FROM instruments;
   SELECT has_table_privilege('lemarket_app', 'audit_log', 'UPDATE') AS app_can_change_audit;
   SQL
   ./run-leap-sql.sh "$TEAM" check.sql
   ```
   Expect `51` instruments and `f` (false).

Load **once**, into the empty database. For later schema changes, run only the new numbered files.

## Step 3: check the task definitions

The pipeline only swaps the image in each service; everything else comes from the stack's task definitions. The
variables each service needs are in [README.md](README.md#task-definition-settings). To see what a service has now:

```bash
TEAM=team3
for svc in orders auth holdings reporting; do
  TD=$(aws ecs describe-services --region us-east-1 --cluster "leap-$TEAM" --services "leap-$TEAM-$svc" \
       --query 'services[0].taskDefinition' --output text)
  echo "== $svc ($TD)"
  aws ecs describe-task-definition --region us-east-1 --task-definition "$TD" \
    --query 'taskDefinition.containerDefinitions[0].{name:name,port:portMappings[0].containerPort,env:environment[].name,secrets:secrets[].name}'
done
```

Check: the container `name` is `orders`, `auth`, `holdings` or `reporting` (the pipeline looks for it);
`SPRING_DATASOURCE_USERNAME` is `lemarket_app`; the port matches the target group, or `SERVER_PORT` is set.

## Step 4: run the pipeline

On the class Jenkins, open the deployment job, choose **Build with Parameters**, pick your `TEAM_ID`, and build.

| Stage | What it means if it fails |
|---|---|
| Validate AWS credentials and stack | Wrong team, or the job may not assume `leap-<team>-deploy`. See [The pipeline](#the-pipeline) |
| Build Docker images | Maven failed inside the image build; read the first `[ERROR]` |
| Build Angular applications | `npm ci` or `ng build` failed; read the first error |
| Publish images and Angular files | ECR, S3 or CloudFront refused the deploy role |
| Instructor approval | Waits up to 30 minutes for an instructor to approve |
| Deploy images to existing ECS services | A service never became stable: its tasks keep stopping. See [ECS tasks](#ecs-tasks-that-stop-or-restart) |
| Backend health checks | A target group never saw its service healthy. See [Load balancer](#load-balancer-and-health-checks) |
| Route checks | CloudFront or the load balancer sends a path to the wrong place. See [CloudFront](#cloudfront-and-the-web-apps) |

A route check's `401` is a pass: the request reached a service, which asked for a login.

## Step 5: check it by hand

On the main CloudFront domain (the trading app) and the admin one (the staff app). Every seed login's password is
`Pass123!`.

1. Trading app: sign in as `seed_trader@seed.lemarket.com`. The Overview shows holdings and moving prices.
2. Place a small BUY. During US market hours it fills; outside them it waits as DELAYED, which is correct.
3. Open `/dashboard` directly in a new tab: the app loads (deep links work).
4. Staff app: sign in as `ops@seed.lemarket.com`. Trade search finds your order and its timeline.
5. Staff app: sign in as `analyst@seed.lemarket.com`. The dashboard tiles load.
6. Leave a page alone for a minute: a warning appears, then you are signed out. That is the inactivity timeout (BR-03).

## Where to look when something fails

| Question | Where |
|---|---|
| Why did a task stop? | ECS, the cluster, the service, **Tasks** (stopped), the task's "Stopped reason"; or `aws ecs describe-tasks` |
| What did the service log? | CloudWatch Logs, the log group named in the task definition's `logConfiguration` |
| Is the load balancer happy? | EC2, Target groups, the group's **Targets** tab, health status and reason |
| What did ECS do recently? | ECS, the service, **Events** tab (newest first) |
| What did a request return? | `curl -i https://<cloudfront domain>/api/...` from your laptop |

```bash
TEAM=team3
# The service's latest events (deployments, failed health checks, stopped tasks)
aws ecs describe-services --region us-east-1 --cluster "leap-$TEAM" --services "leap-$TEAM-orders" \
  --query 'services[0].events[:10].[createdAt,message]' --output table

# Follow a service's logs (use the log group from the task definition)
aws logs tail <log-group-name> --region us-east-1 --since 30m --follow

# A target group's health, with the reason when unhealthy
aws elbv2 describe-target-health --region us-east-1 --target-group-arn <target-group-arn> \
  --query 'TargetHealthDescriptions[].[Target.Id,TargetHealth.State,TargetHealth.Reason,TargetHealth.Description]' --output table
```

## Hiccups and fixes

Each entry: what you see, why, and what to do.

### Loading the database

**`Could not connect to the database` from `run-leap-sql.sh`.**
You are in a normal CloudShell, not a VPC one, or the VPC environment has the wrong subnet or security group. Create
the VPC environment again with a private subnet of the `leap-<team>` VPC and the stack's **TasksSecurityGroup**.

**`invalid command \getenv` while loading 016.**
The `psql` client is older than version 15. The class script installs `postgresql15` when `psql` is missing; if an
older one is already there, run `sudo dnf install -y postgresql15` and load again.

**`syntax error at or near "TRIGGER"`.**
The database is older than PostgreSQL 14 (the migrations use `CREATE OR REPLACE TRIGGER`). The instructor needs to
upgrade the RDS engine version.

**`Role lemarket_app does not exist and leapadmin may not create it`.**
The master user cannot create logins, which is unusual on RDS. Ask the instructor to run, as the master user:
`CREATE ROLE lemarket_app LOGIN PASSWORD '<the password from Step 1>';`, then load again.

**You loaded without `APP_DB_PASSWORD` set.**
`lemarket_app` got the development password `changeme_app`. Loading again does not fix it: 016 only creates the login
when it is missing. Change it once, as the master user, to the value in the secret:
`ALTER ROLE lemarket_app PASSWORD '<the password from Step 1>';`

**`already exists` or `duplicate key` errors when loading a second time.**
Files 001 to 003 can only run on an empty database. Never reload the whole folder into a loaded database: run only the
new numbered files. To start over, the instructor recreates the database.

**The load stopped half-way.**
The script stops at the first error (`ON_ERROR_STOP`); every file before it is in. Fix the cause, then run the remaining
files only, starting with the one that failed.

### ECS tasks that stop or restart

**Log says `Database account 'leapadmin' may change or delete audit records`.**
The service is logging in as the master user, and refuses to start that way, on purpose. Set
`SPRING_DATASOURCE_USERNAME=lemarket_app` and its password from the Step 1 secret in that task definition.

**Log says `password authentication failed for user "lemarket_app"`.**
The task definition's password and the database's differ. Usually the database was loaded without
`APP_DB_PASSWORD` (see above), or the task reads a different secret.

**Log says `Schema-validation: missing table` or `missing column`.**
A migration was not loaded, or an older database is missing newer files. Run the missing numbered files.

**Log shows a connection timeout to the database.**
The task cannot reach RDS. The tasks must use the stack's TasksSecurityGroup, which the database allows on 5432.

**Stopped reason `ResourceInitializationError: unable to pull secrets or registry auth`.**
The task's execution role may not read the secret, or ECR. Ask the instructor to give the execution role
`secretsmanager:GetSecretValue` on our secrets.

**Stopped reason `CannotPullContainerError`.**
The image tag is not in ECR, or the execution role cannot pull from it. Rerun the pipeline; it publishes before it
deploys.

**Log says `exec format error`.**
The image was built for ARM (for example `docker build` on an Apple-silicon Mac) and pushed by hand. Let the pipeline
build it: it builds for `linux/amd64`.

**Tasks start, then are killed after a minute or two, over and over.**
The load balancer marks them unhealthy before Spring Boot has finished starting. Ask the instructor for a health
check grace period of about 120 seconds on each ECS service.

### Load balancer and health checks

**Targets unhealthy, reason `Target.ResponseCodeMismatch`.**
The health check path is not `/health`, or it expects another code. Every service answers `GET /health` with 200
when it can reach the database, and 503 when it cannot: check the logs for a database error.

**Targets unhealthy, reason `Target.Timeout` or `Target.FailedHealthChecks`.**
The target group's port is not the container's port, or the tasks' security group does not allow the load
balancer on that port. Our services listen on 8082 (auth), 8084 (holdings), 8085 (orders) and 8086 (reporting),
unless `SERVER_PORT` says otherwise.

**No targets registered.**
The ECS service is not attached to its target group. Instructor fix, in the stack.

**`404` from every API call (the load balancer's fixed response).**
The listener still has the class example's rules (`/api/orders/*`, `/api/holdings/*`). They need to be ours:
[the rules](README.md#1-load-balancer-rules).

**`502 Bad Gateway`.**
The target answered something the load balancer could not use, usually because the task was restarting. Check the
service's events and logs.

**`503 Service Unavailable`.**
No healthy targets for that path. Fix the health checks first.

### CloudFront and the web apps

**An API call returns the Angular page (HTML) instead of JSON.**
The distribution has custom error responses that turn 403 and 404 into `/index.html`. They apply to `/api/*` too,
so a refused request looks like a page. Remove them and use the CloudFront Function in
[README.md](README.md#3-cloudfront-on-both-distributions).

**Opening a deep link (`/dashboard`, `/analyst`) shows `AccessDenied` XML.**
The opposite problem: no fallback at all. Attach that same CloudFront Function to the default behavior.

**Sign-in works, but the next call says 401 and you are back at the login page.**
CloudFront is not forwarding cookies on `/api/*`. The behavior needs an origin request policy that forwards all
cookies (`AllViewerExceptHostHeader`) and caching disabled.

**A browser CORS error.**
The app is being called on the load balancer's address instead of through CloudFront. Always use the CloudFront
domains: the apps call `/api` on their own origin and need no CORS.

**The site still shows the old version after a deploy.**
CloudFront or your browser kept the old files. The pipeline invalidates `/*` on both distributions; wait a minute,
then hard-refresh (Shift + reload).

**The staff app signs you out of the trading app, or the other way round.**
They are open on the same domain. Each must have its own CloudFront distribution: main for the trading app, admin
for the staff app.

### The pipeline

**`AccessDenied` on `sts:AssumeRole` in the first AWS stage.**
The wrong `TEAM_ID` was picked, or the class Jenkins may not assume `leap-<team>-deploy`. Check the parameter, then
ask the instructor.

**`Wrong team stack`.**
The stack's `TeamId` output differs from `TEAM_ID`. You picked another team.

**`Missing ECS service leap-<team>-…`.**
The stack has not created that service. Instructor fix.

**`jq: error` right after `describe-task-definition`.**
The task definition has no container named `orders`, `auth`, `holdings` or `reporting`. The pipeline refuses to
change a container it cannot identify. Instructor fix: the container name must match the slot.

**`Wrong MAIN_DIST_PATH` or `Wrong ADMIN_DIST_PATH`.**
The Angular build put its files elsewhere. The paths are `apps/frontend/dist/lemarket-ui/browser` and
`apps/frontend/dist/staff/browser`; if an Angular upgrade changed them, update `Jenkinsfile.aws`.

**`npm ci` fails with an engine or lockfile error.**
The Jenkins `NodeJS` tool is too old or too new for Angular. Use Node 22 or 24 for the `NodeJS` tool.

**The Docker build takes very long or runs out of space.**
Each image builds the backend with Maven inside Docker. Clear old images on the Jenkins machine
(`docker image prune -a`) and run again.

**Stuck at "Instructor approval".**
Nothing is wrong: it waits up to 30 minutes for an instructor to approve, then stops.

**Route check `FAIL … expected 401 as JSON` with `text/html`.**
See "An API call returns the Angular page" above.

### The app runs but something looks wrong

**Orders stay PENDING and never fill.**
orders cannot reach holdings to price or settle them. Check `MARKET_SERVICE_URL` and `HOLDINGS_SERVICE_URL` in the
orders task definition, that `INTERNAL_API_TOKEN` is the same in orders and holdings, and that the tasks may reach
each other's ports. The orders log shows the failing call.

**Orders wait as DELAYED.**
The market is closed (outside US trading hours or on a holiday). They fill when it opens. This is correct.

**Prices jump between page loads.**
holdings runs more than one task, each simulating its own market. Set its desired count to 1.

**No notifications, an empty Alerts page for Trading Ops, no market activity.**
There is no Kafka broker: the order events are only written to the log. Everything else works. With a broker, set
the Kafka variables listed in [README.md](README.md#task-definition-settings).

**Signed out after a minute without touching anything.**
That is the inactivity timeout (BR-03), 1 minute by default. For a long demo, set
`LMJ_SESSION_INACTIVITYTIMEOUT` (for example `PT10M`) in the auth task definition.

**Live order and portfolio updates pause now and then.**
CloudFront closes a quiet live connection after 30 to 60 seconds; the app reconnects and catches up by itself.

**Can't sign in with a seed login.**
The password is `Pass123!` for every seed login, client or staff. Three wrong tries lock a login for 15 minutes
(the lockout rule); wait, or use another seed login.
