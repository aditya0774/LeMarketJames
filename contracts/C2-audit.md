# C2 – Audit event format

Each lifecycle story writes its own audit events, so the audit trail doesn't wait on execution or any other story.

## Where it's defined

| What | Code |
|---|---|
| Event types, and the `details` keys each one carries | [AuditEventType.java](../libs/common/src/main/java/com/lemarketjames/common/audit/AuditEventType.java) |
| Stored fields (the `audit_log` table) | [AuditEventEntity.java](../libs/common/src/main/java/com/lemarketjames/common/audit/AuditEventEntity.java), table reshaped by [010](../database/schema/010_shared_contracts.sql) |
| The only way to write | [AuditRecorder.java](../libs/common/src/main/java/com/lemarketjames/common/audit/AuditRecorder.java) |

Each event records the order, its account, the client (resolved from the account), when it happened as a UTC instant, and a JSON `details` object.

## Rules

- **Written in the same transaction as the change it describes.** `AuditRecorder.record` is `@Transactional(propagation = MANDATORY)`, so calling it outside a transaction fails, and the event commits or rolls back with the change.
- **Written only through `AuditRecorder`.** Never insert into `audit_log` directly. `order_events` from 001 is an unused early draft; don't write to it.
- **Each service audits what it does:**

  | Where | Event types |
  |---|---|
  | buy-sell-service, order placement | `SUBMITTED`, then `VALIDATED` |
  | buy-sell-service, status changes | `ACCEPTED`, `FILLED`, `REJECTED` |
  | holdings-service, settlement | `SETTLED` |

  Holdings commits `SETTLED` with the cash/share changes and its idempotency receipt. Buy-sell
  then commits `FILLED` and the final order status. These are separate transactions: after a lost
  response, `SETTLED` can temporarily exist without `FILLED`. The persisted execution intent is
  retried with the same price; holdings returns its original receipt without moving funds again,
  and buy-sell records `FILLED` once. Thus `SETTLED` precedes `FILLED` in the completed audit trail.
- **Kept, not deleted.** Events older than the online retention window ([C5](C5-config.md)) are marked `archived` rather than removed. The archival job and the audit views are later stories. Their API is planned in [C6](C6-api.md#planned-agreed-not-built).

## Mirrors (change together with `AuditEventType`)

- The `audit_log.action` CHECK constraint ([010](../database/schema/010_shared_contracts.sql)).
- The seeded events in [011](../database/schema/011_seed_test_data.sql).
