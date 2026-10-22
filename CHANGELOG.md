# Changelog

## Unreleased

- BR-16: A real overnight report and a separate read side. The overnight stub is replaced by the
  Monthly trading summary: each night it rebuilds the nightly trade summary (`reporting_daily_trades`,
  migration 030: one row per day, stock, segment and side, built only from `reporting_trades`) through
  the run date, then writes the month so far as a CSV per stock and segment, stored with the run
  (`report_outputs`). The overnight list gives each run's `outputFileName`, and the new
  `GET /api/v1/reports/overnight/{runId}/output` downloads it (a Download CSV link on the staff app's
  Overnight reports). `/api/v1/reports/trades` and `/trades-by-stock` read the summary for the days it
  covers and the live view only after it. In the report load test, reports answered 9,840 requests in
  30 s instead of 607 with order confirmations still on target (staff-ops-service/LMKT-44-performance.md).
  New switches `REPORTING_OVERNIGHT_QUERY_TIMEOUT_MS` and `REPORTING_OVERNIGHT_FAILWITH` (was
  `REPORTING_OVERNIGHT_STUB_FAILWITH`) ([C6](contracts/C6-api.md#get-apiv1reportsovernight), [C5](contracts/C5-config.md#reporting-read-side-limits)).
- BR-14: Trade records are kept and can't be changed. Migration 029 locks them like the audit trail:
  an order is never deleted and, once FILLED or REJECTED, never updated; `settlement_receipts`,
  `cash_movement_receipts` and `account_status_changes` are insert-only, for `lemarket_app` and (by
  trigger) for the owner too. Audit events older than `lmj.audit.online-retention` count as archived:
  the order timeline and trade search return `archived` and the staff app labels them, and the new
  `GET /api/v1/audit/archive` (orders-service, Trading Ops, staff app) retrieves them by order,
  request, client and time, with no age limit. Backups: `scripts/backup-db.sh`,
  `scripts/windows/backup-db.ps1` and a nightly `db-backup` Compose service (`pg_dump`, keeping 7);
  restore steps in database/README.md. Tests that insert orders remove them as the owner: the e2e
  suite now needs `E2E_OWNER_DATABASE_URL` ([C2](contracts/C2-audit.md#retention-and-the-archive),
  [C6](contracts/C6-api.md#audit-archive-trading_ops-only), [C5](contracts/C5-config.md#who-uses-each-setting)).
- BR-17: Insights are flagged when they move. Every Analyst insight now carries a `flag`
  (`flagged`, `changePercent`, `direction`, `thresholdPercent`): flagged when it moved by
  `lmj.reports.insight-flag-percent` or more against the period it is compared with.
  The most active stocks are compared with the same days of last week (`previousTradedValue`), and
  the active-client trend's last seven days with the seven before (`lastWeek`, `weekBefore`). The
  Analyst dashboard opens with a "What changed" list, flagged insights first, and each tile shows a
  flag chip. Trading Ops get an Alerts page (`/alerts`, beside trade search on a new tab bar) listing
  the surveillance alerts, filtered by reason and UTC days (`GET /api/v1/surveillance/alerts` takes
  `reason`, `from` and `to`), each order linked to its timeline ([C6](contracts/C6-api.md#insight-flags),
  [C5](contracts/C5-config.md#who-uses-each-setting), [C7](contracts/C7-roles.md)).
- BR-04: Orders can be placed by amount. `POST /api/v1/orders` and `/api/v1/buy-orders` take
  `amount` (USD) instead of `quantity`, exactly one of the two; the server buys or sells as many whole
  shares as the amount covers at the ask (BUY) or bid (SELL) and keeps the amount on the order
  (`orders.requested_amount`, migration 031; `requestedAmount` in the order DTO). Orders are whole
  shares only, now enforced on the server too: the new placement check `QUANTITY` refuses `0.5` or an
  amount below one share's price with the new code `INVALID_QUANTITY`. The trade form (and the
  Overview's quick trade) has a Shares / Amount switch and shows "Buys N shares, about $X left over"
  before the client confirms ([C6](contracts/C6-api.md#post-apiv1orders), [C1](contracts/C1-orders.md),
  [C2](contracts/C2-audit.md#the-submission-trail)).
- BR-05: Open orders now reserve what they will spend. A BUY must fit the cash less the cost of the
  account's open BUYs (the buying power the balance shows), and a SELL the holding less the shares in
  its open SELLs of that stock; trading-service's `POST /internal/holdings/validate` takes the new
  `reservedQuantity` for that. Placement locks the account until the order is saved, so two orders
  sent at once are checked one after the other (`OrderPlacement`). Location rules can now name single
  stocks: `instrument_location_restrictions` (migration 028) blocks the six non-US companies' stocks
  for clients living in New Jersey, shown by the new persona `seed_restricted`; the platform-wide
  `lmj.orders.restricted-locations` list still blocks every stock ([C6](contracts/C6-api.md#post-apiv1orders),
  [C5](contracts/C5-config.md#who-uses-each-setting), [C3](contracts/C3-seed-data.md#clients)).
- BR-03: Accounts can be suspended, reinstated, closed and expired. Trading Ops do it from trade
  search in the staff app: once a client is chosen, an "Account status" card shows their status and
  its history, and changes it with a reason and a confirmation step
  (`GET`/`PUT /api/v1/clients/{clientId}/status`, auth-service, `TRADING_OPS` only, routed by the staff
  app). A client closes their own account from the new account page in the trading app's account
  menu (`GET`/`POST /api/auth/close-account`): only with no stocks and no open orders, after
  re-entering the password; any cash stays on the closed account for operations to pay out. Every
  change is kept in the new `account_status_changes` table (migration 027, insert-only for the
  services), logged as `ACCOUNT_STATUS_CHANGED`, and ends the client's sessions: their next request
  gets `401 ACCOUNT_INACTIVE` ([C6](contracts/C6-api.md#account-status-trading_ops-only),
  [C7](contracts/C7-roles.md#who-may-use-what)).
- BR-03: Sessions end after one minute without input (`lmj.session.inactivity-timeout`, `PT1M`).
  A session token, and its cookie, last only that long. `POST /api/auth/refresh` extends an active
  session, never past `jwt.expiration-ms` from sign-in (the token's new `auth_time` claim). Login
  and `/api/auth/me` now also return `expiresInSeconds` and `inactivityTimeoutSeconds`. Both web
  apps refresh on user input only, at most every 20 seconds, warn 30 seconds before the end ("Are
  you still there?", `@lemarket/shared-ui` `SessionTimeout`) and then sign out to
  `/login?reason=timeout`. The staff app routes the refresh. Test stacks set
  `SESSION_INACTIVITY_TIMEOUT=PT30M` (Docker Compose; Jenkins does), or `-SessionTimeout PT30M` in
  `start-all.ps1` ([C5](contracts/C5-config.md#who-uses-each-setting),
  [C6](contracts/C6-api.md#auth-auth-service)).
- BR-02 hardening: both web apps are HTTPS only in Docker: `https://localhost:4200` and `:4201`,
  with a self-signed certificate made at image build (accept the browser warning once). Plain HTTP
  on those ports is redirected. The session cookie is `Secure` (`AUTH_COOKIE_SECURE=true`). The
  services', database's and Kafka's ports are published on `127.0.0.1` only
  (`SERVICE_BIND_ADDRESS`; a shared VM sets `0.0.0.0`). `APP_CORS_ALLOWED_ORIGIN` is now a
  comma-separated list, by default the https and http localhost origins. `start-all.ps1` serves both
  dev servers with `--ssl`. The e2e tests, the Jenkins smoke tests and `scripts/verify-*.sh` use
  `https://` and accept the certificate ([README](README.md#https-and-local-only-ports),
  [C5](contracts/C5-config.md#web-apps-ports-and-service-urls)).
- BR-02: No endpoint answers without a credential, except health, registration, sign-in and
  `GET /api/market/**`. `/internal/**` in trading- and orders-service (settlement, the SELL check
  and the test controls) now needs the shared `INTERNAL_API_TOKEN` in the `X-Internal-Token`
  header. Without it the answer is `401`, and with a client or staff session `403`
  (`InternalApiTokenFilter` in `libs/common`). orders-service sends the token, and Docker Compose
  gives both services the same value. `/api/market/**` is open for `GET` only. A stack outside
  development must set `INTERNAL_API_TOKEN` ([C6](contracts/C6-api.md#internal-endpoints-service-to-service-only-never-through-a-web-app),
  [C5](contracts/C5-config.md#who-uses-each-setting)).
- BR-15 follow-up: the e2e audit-trail expectations now include the `PENDING` step, and the quote
  on a `STALE_QUOTE` rejection.
- BR-01: Registration rules are enforced on the server, not only in the form. auth-service's
  `RegistrationRules` applies the form's rules with the same messages (username, password strength,
  email, phone, SSN, ZIP, names and address, age, minimum deposit, experience and employment), and
  registration is one transaction. The form now sends the username the client typed, not the email
  ([C6](contracts/C6-api.md#auth-auth-service)).
- BR-15: Every step of an order's life is in its audit trail. `PENDING` and `DELAYED` are audited
  (migration 026; `DELAYED` with the reason `MARKET_CLOSED`), and a rejection made on a quote records
  that quote's price and time. The staff timeline shows a waiting step in grey
  ([C2](contracts/C2-audit.md#the-execution-trail)).
- BR-12: The market in scope is the 51 US equities in USD; README "Market coverage (BR-12)" sets out
  the scope, the simulated feed and how quote freshness is enforced at placement and execution
  ([C4](contracts/C4-quote-feed.md#market-scope)).
- BR-18: README "Additional capability (BR-18)" justifies the Learn tab and stock simulator against
  the alternatives considered (copy trading, a mobile app).

- LMKT-28: Holdings and buying power on the Overview update live when an order settles, and
  catch up after the connection drops. trading-service adds `GET /api/v1/portfolio/stream`
  (SSE, `balance-changed`), sent only after a settlement commits; the trading app's Nginx routes
  it unbuffered ([C6](contracts/C6-api.md#get-apiv1portfoliostream)). E2E:
  `apps/e2e/tests/holdings-live.spec.ts`.
- Seven services instead of thirteen, with every feature kept. The stack is now two web apps,
  four microservices and Kafka:
  - The trading app (4200) and the staff app (4201).
  - `auth-service` (8082).
  - `orders-service` (8085), formerly `buy-sell-service`.
  - `trading-service` (8084), which merges `core-`, `market-`, `holdings-`, `notification-` and
    `activity-service`.
  - `staff-ops-service` (8086), which merges `reporting-` and `surveillance-service`.
  - Kafka (9092).

  Details:
  - **No gateways.** Both gateways are gone (host ports 8089 and 8090), and so is the unused
    `staff-gateway-service` module. Each web app's Nginx, and its dev-server proxy under
    `ng serve`, now routes `/api` straight to the services ([C6](contracts/C6-api.md#which-web-app-routes-which-paths)).
  - **Staff routing.** The staff app keeps the staff gateway's behaviour: only staff paths exist,
    the browser holds `staff_jwt` while the services see `jwt`, and `Origin` is not passed on
    ([C7](contracts/C7-roles.md#the-staff-session-cookie)).
  - **In-process market.** trading-service reads its market simulator in-process. Its
    `InProcessMarketData` and `InProcessPriceHistory` keep the quote-feed outage behaviour of the
    HTTP client ([C4](contracts/C4-quote-feed.md)).
  - **Moved ports.** The market test controls moved from 8083 to 8084 (`E2E_MARKET_URL`), and
    the surveillance alerts from 8088 to 8086.
  - **Reports keep their own pool.** In staff-ops-service, reports read through a read-only,
    time-limited pool of their own (`reporting.datasource.hikari.*`); surveillance writes through
    the ordinary pool.
  - **Kafka groups unchanged.** The three consumer groups keep their names.
  - **Ping.** `GET /api/v1/reports/ping` now answers `"service": "staff-ops-service"`.
  - **No migrations.** No schema change; no migration needed.
- LMKT-44: Add the report load test (`apps/e2e/load/report-load.spec.ts`, `npm run test:load`)
  and a Jenkins **Run report load test** stage. Over a year of inserted trades, it times client
  orders from placement to FILLED, first alone and then while analysts run reports nonstop
  through the staff gateway. It fails if p95 under load exceeds the baseline's allowance or an
  absolute cap. The timings of both phases are attached to the report as `load-summary.json`.
  Its first runs failed (p95 1.5 s → 3.4 s), so `GET /api/v1/reports/trades` now groups in
  SQL instead of loading every trade into the service. That also fixes its periods to use
  the reports time zone; evening trades used to land on the next day. And reporting-service's
  pool defaults to 1 connection (`REPORTING_DB_POOL_SIZE`), since each busy report connection
  takes a CPU core from order execution. The test now passes.
- LMKT-72, LMKT-74: Buy and sell settlement tested end to end. `apps/e2e/tests/settlement.spec.ts`
  follows real fills from the executor through settlement on a running stack: a buy the client
  can no longer afford, or a sell of shares that are gone, by fill time is `REJECTED` with
  `INSUFFICIENT_CASH` or `INSUFFICIENT_HOLDINGS`, changes nothing, and its alert is read by a
  Trading Ops login on the staff app; a settlement that fails part-way changes nothing and
  settles exactly once afterwards, with `SETTLED` carrying the cash and holding changes. For the
  failure, `holdings-service` gains a settlement test control,
  `/internal/holdings/control/settlement-faults/{accountId}`, which exists only where
  `lmj.settlement.control-enabled` is on (Docker Compose, `start-all.ps1`). No behaviour changes
  without it. The `feed-failure` Playwright project now also waits for `execution-recovery`.
- LMKT-73: Sell settlement checked against S18's acceptance criteria. No behaviour changes:
  `holdings-service` already credited the cash, reduced or removed the holding and audited
  `SETTLED` in one transaction. New tests prove it for a sell through
  `POST /internal/holdings/settle`, including a sell that fails part-way (nothing changes, even
  when the holding had been deleted) and a sell for more shares than are held.
- LMKT-71: Trading Ops are alerted when a settlement is refused. `OrderStatusChanged` carries a
  new `rejectionReason` (null unless the order was rejected), and `surveillance-service` now also
  reads `lemarket.orders.status-changed` and raises a `SETTLEMENT_REFUSED` alert when an order
  is rejected with `INSUFFICIENT_CASH` or `INSUFFICIENT_HOLDINGS`. Migration 019 keys
  `order_alerts` by order and reason, so a large order that is refused has both alerts, and adds
  `rejection_reason` and `occurred_at`; `GET /api/v1/surveillance/alerts` returns them as
  `rejectionReason` and `occurredAt` and lists alerts newest first by `occurredAt`. Apply 019
  before starting the updated service. A new integration test proves a settlement that fails
  part-way changes nothing and settles once when retried.
- Kafka consumers: three new services read the order events, one topic each, in Docker Compose
  and Jenkins. `notification-service` (8087) keeps a notification per order status change for
  the order's client (`GET /api/v1/notifications`); `surveillance-service` (8088) raises an
  alert for Trading Ops on each order of at least `lmj.surveillance.large-order-quantity` shares
  (`GET /api/v1/surveillance/alerts`, staff gateway only); `activity-service` (8091) sums fills
  into traded volume per stock over 24 hours (`GET /api/v1/market-activity`). They listen when
  `LMJ_EVENTS_CONSUMER=kafka`, store the same event only once, and keep their results in the
  tables of migration 018. The topic names moved to `libs/common` (`OrderEventTopicNames`) so
  publisher and consumers share them. Jenkins gains a `docker ps` stage and a
  **Verify Kafka consumers** stage (`scripts/verify-kafka-consumers.sh`).
- LMKT-137: Add a single-node Kafka broker (KRaft, port 9092) to Docker Compose and Jenkins.
  `buy-sell-service` publishes `OrderStatusChanged` and `OrderFilled` to it as JSON, keyed by
  order id, when `LMJ_EVENTS_PUBLISHER=kafka` (set in Compose); without it the stub publisher
  still only logs them. `scripts/verify-buy-order.sh` now reads the fill from the topic.
  A third event, `OrderSubmitted`, announces each new order on `lemarket.orders.submitted`, so a
  consumer can follow an order from placement; all three events are described in contract C6.
- LMKT-138: Add `reporting-service` (port 8086) for analyst reports under `/api/v1/reports/**`,
  with `GET /api/v1/reports/ping`, `ANALYST`-only access, a shared report date-range helper in
  the `lmj.reports.time-zone` zone, and read-side limits (small pool, read-only, query timeout).
  Planned report paths move from `/api/reports/...` to `/api/v1/reports/...`; reports are no
  longer open to `COMPLIANCE`, and the planned activity response loses its `fee` field.
- LMKT-91: Add optional `date` and `timeZone` filters to account order history and
  status-filtered history, using local calendar boundaries and UTC placement times.
  Preserve array responses, ownership checks, and unfiltered requests.
- New order entity timestamps use UTC explicitly. Existing non-UTC timestamp data
  requires source-zone-aware conversion; frontend wiring remains LMKT-92.
