# LMKT-32: Sell order integration

The dashboard trade dialog posts SELL orders through the frontend proxy and gateway to
`POST /api/v1/orders` in core-service. It uses typed request/response models and the
environment API base URL. Existing client holdings validation gives early feedback;
OrderService independently checks account ownership, tradability and holdings before
saving inside a transaction. HoldingsService owns the holdings business rule, keeping
the order service dependent on a feature service rather than duplicating repository logic.

The response is HTTP 201 with a numeric ID, timestamps and SUBMITTED status. The dialog
shows that ID/status, prevents repeated submission while waiting, and recovers from
validation, session and network failures. Existing order entities, foreign keys, indexes
and the Compose PostgreSQL volume already support SELL records; no schema migration or
new microservice is needed.

This is submission only: shares are not reserved or removed and cash is not credited.
Open orders do not reduce the holdings check; the execution flow must revalidate shares.

## Verification

- `npm test -- --watch=false` and `npm run build` in apps/frontend.
- `mvn -B test` at the repository root.
- Jenkins runs SellOrderIntegrationTest against disposable PostgreSQL before image builds.
  This test has no surrounding test transaction: it verifies committed SQL rows and API
  readback, plus rejection without insertion for invalid/unauthorized requests and missing
  or insufficient holdings.
- Jenkins runs `bash scripts/verify-sell-order.sh` after starting the stack. It registers
  a test account through the frontend/gateway, seeds its existing holding, submits a SELL,
  verifies the PostgreSQL row and checks API readback after restarting core-service.

The integration suite uses H2 by default. Docker/PostgreSQL and restart results must be
confirmed by the Jenkins run; local unit tests alone do not prove those stages passed.
