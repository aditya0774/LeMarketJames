# IA-08 testing and EC2 deployment

## Local checks (PowerShell)

From the repository root:

```powershell
cd apps/frontend
npm test -- --watch=false
npm run build
cd ../backend
mvn -B "-Dmaven.repo.local=C:/Users/Administrator/.m2/repository" test
```

Frontend tests cover account state, private-data cache clearing, loading/empty/error views, and SELL validation. `OwnDataIntegrationTest` uses two persisted accounts and real JWT cookies to verify own access, denied foreign reads/writes, instrument search isolation, empty holdings, anonymous rejection, session ownership, and login using persisted credentials. Normal backend tests use H2; Jenkins repeats the isolation tests against PostgreSQL with schema validation enabled.

## Docker on EC2

Commit and push the reviewed changes, including new files, then SSH into EC2 and pull the same branch in the repository directory. Docker/Compose run on EC2, not Windows. No database migration is needed for IA-08; existing foreign keys and username uniqueness support the ownership queries.

Create a gitignored `.env` in the EC2 repository root containing `DB_PASSWORD` and `JWT_SECRET`. Use a long random database password and at least 32 random bytes for the JWT secret. For a fresh testing checkout with no `.env`, this creates both without printing them:

```bash
umask 077
if [ ! -e .env ]; then
  printf 'DB_PASSWORD=%s\nJWT_SECRET=%s\n' "$(openssl rand -hex 24)" "$(openssl rand -hex 32)" > .env
fi
```

If `.env` already exists, ensure both variables are set. Then run on EC2:

```bash
docker compose down -v --remove-orphans
docker compose up -d --build
docker compose ps
curl --fail http://localhost:8081/actuator/health
```

The `-v` reset is intentional for testing: it deletes stored users, orders, and holdings. Remove `-v` here and in both Jenkins cleanup locations before keeping real data.

For browser testing, use your HTTPS frontend URL. Alternatively, open an SSH tunnel from your local machine:

```bash
ssh -i /path/to/key.pem -L 4200:localhost:4200 ec2-user@YOUR_EC2_HOST
```

Visit `http://localhost:4200` through the tunnel. Authentication cookies stay `Secure`; accessing the frontend directly over plain HTTP on an EC2 public IP is not the supported login path. Nginx proxies `/api/` to the backend inside Docker, so no private EC2 IP is embedded in JavaScript.

Register two users. Verify each can sign in and reach `/holdings`; new accounts show the empty state. Automated tests seed holdings/orders and verify that tampering with account and order IDs returns `403 ACCOUNT_ACCESS_DENIED` without exposing another user's data.

## Jenkins

Run Jenkins after pushing the changes and configuring these **Secret text** credentials:

- `lemarket-db-password`: PostgreSQL testing password.
- `lemarket-jwt-secret`: JWT signing secret, at least 32 bytes.

The agent needs configured `JDK21` and `NodeJS` tools, Maven, Docker Compose, Docker access, and available ports 4200, 8081, and 5432. Use the same EC2 repository/Compose project for a manual run and Jenkins, or stop the manual stack before Jenkins uses those ports. Do not run both against conflicting ports.

The pipeline runs Angular tests/build, resets the testing database, runs backend tests, builds versioned images, starts PostgreSQL and the apps, applies existing schema updates, and reruns the own-data integration tests against PostgreSQL. It then runs existing health/API smoke tests. PostgreSQL integration tests roll back fixtures. Jenkins cleanup stops the stack and deletes the testing volume at the end, including smoke-test users. For browser testing after Jenkins, run the Docker commands above again on EC2.

Verify that **Test and build Angular**, **Test with Maven**, **Build versioned Docker images**, **Verify own-data isolation on PostgreSQL**, and all smoke stages are green. Local tests do not establish that EC2 images or Jenkins passed; check those results on the server.

## Local Angular development

`npm start` uses the Angular proxy to `http://localhost:8081`. To use the EC2 backend from local Angular development, also forward port 8081 with SSH. Keep the browser on localhost or HTTPS for authentication cookies.
