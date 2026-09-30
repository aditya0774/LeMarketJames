# Order history end-to-end tests (LMKT-92)

These tests exercise Angular -> gateway -> auth/core services -> PostgreSQL. No API
responses are mocked. Each test registers a unique client via the real API, seeds
15 orders for that account, logs in through the browser, and removes only its own
client/account/orders afterward. Test passwords are generated for each run.

The fixture in `order-history.spec.ts` spans 2023-2026, leap day, both New York DST
transitions, and exact UTC/local period boundaries. Fill dates differ from placement
dates. Tests assert both the real HTTP response and rendered rows for AC1; AC2 checks
the exact empty message; AC3 clears both filters and visits every restored page.

## Run against a disposable stack

1. Start the full stack with the current branch's images: `docker compose up -d --build`.
   Use a disposable development database with all numbered schema scripts applied.
2. From `apps/frontend`, run `npm ci` and `npx playwright install chromium`.
3. Set `E2E_DATABASE_URL` to that same PostgreSQL database and optionally set
   `E2E_BASE_URL` (default `http://localhost:4200`). For example, in PowerShell:

   ```powershell
   $env:E2E_DATABASE_URL = 'postgresql://lemarket:changeme@localhost:5432/lemarket'
   npm run e2e
   ```

   Use your development database credentials; never point this suite at production.
   The example matches the Compose development defaults.

All five backend services must be healthy before running. The Compose frontend
proxies `/api` to the gateway. When running services locally instead, `npm start`
uses `proxy.conf.json` and expects the gateway on port 8089. The database URL is
required explicitly to prevent silently seeding a different database.

Run `npm test -- --watch=false` for unit tests and `npm run build` for the production
build. End-to-end tests run in Chromium with `America/New_York` as the browser zone,
one worker, and retain traces on failure in ignored `test-results/`. Open the HTML
report with `npx playwright show-report`. These browser tests are a separate command;
the existing Jenkins pipeline does not automatically run them.

On memory-constrained hosts, run frontend builds and tests separately, set
`NG_BUILD_MAX_WORKERS=2`, and bound each service JVM heap before starting the stack.
