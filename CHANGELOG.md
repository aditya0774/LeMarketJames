# Changelog

## Unreleased

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
