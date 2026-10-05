# C2 – Audit event format

Each lifecycle story writes its own audit events, so the audit trail doesn't wait on execution or any other story.

## Where it's defined

| What | Code |
|---|---|
| Event types, and the `details` keys each one carries | [AuditEventType.java](../libs/common/src/main/java/com/lemarketjames/common/audit/AuditEventType.java) |
| Stored fields (the `audit_log` table) | [AuditEventEntity.java](../libs/common/src/main/java/com/lemarketjames/common/audit/AuditEventEntity.java), table reshaped by [010](../database/schema/010_shared_contracts.sql) and [014](../database/schema/014_submission_audit.sql) |
| The only way to write | [AuditRecorder.java](../libs/common/src/main/java/com/lemarketjames/common/audit/AuditRecorder.java) |
| The placement rules a `RULE_CHECKED` event can name, in the order they run | [ValidationRule.java](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/submission/ValidationRule.java) |
| Reading a trail, and the only operations the app has (insert and read) | [AuditEventRepository.java](../libs/common/src/main/java/com/lemarketjames/common/audit/AuditEventRepository.java) |

Each event records the order, its account, the client, when it happened as a UTC instant taken from the server's clock, and a JSON `details` object.

## Stored fields

| Column | Holds | Empty when |
|---|---|---|
| `order_id` | The order the event is about | The order was refused at submission, so no order exists |
| `account_id` | The order's account | The caller was refused access to the account they asked for |
| `client_id` | For submission events, the authenticated caller. For the others, the client the account belongs to | The caller isn't a client |
| `action` | The event type | never |
| `details` | The keys documented on the event type | never |
| `created_at` | When it happened (UTC instant, server clock) | never |
| `request_id` | The submission the event belongs to | The event isn't a submission event |
| `event_key` | `<request_id>:<type>`, plus `:<rule>` for `RULE_CHECKED`. Unique in the table | The event isn't a submission event |
| `archived` | Reserved, always `false`. An event is never updated, so archival is not recorded on the row (see the rules) | never |

Every row has an `order_id` or a `request_id`.

## The submission trail

Every order submission that reaches placement is audited, whether or not the order is saved. The submission events are `SUBMITTED`, `RULE_CHECKED` and `VALIDATED`, and all the events of one submission share its `request_id`.

- **The request ID** is a UUID issued by buy-sell-service for each `POST /api/v1/orders` and `POST /api/v1/buy-orders`, and returned in the `X-Request-Id` response header on success and on every refusal ([C6](C6-api.md#post-apiv1orders)). The caller can't choose it. It is the only identifier a refused order has.
- **An accepted order** is saved with `SUBMITTED`, one `RULE_CHECKED` per rule (all `PASS`), then `VALIDATED`, which lists the rules that ran. All of them carry the order ID. The order then continues with `ACCEPTED` and the events below.
- **A refused order** saves no order row. Its trail is `SUBMITTED`, then one `RULE_CHECKED` per rule that ran. The last one is the outcome: `FAIL` with `reason`, the same code the caller received. There is no `VALIDATED` and no `REJECTED`; `REJECTED` stays an order status change ([C1](C1-orders.md)).
- **`result` is `PASS`, `FAIL` or `ERROR`.** `ERROR` means the check could not complete (for example holdings-service was unreachable); the caller got an error rather than a refusal, and the trail shows where it stopped.
- **The `reason` on a `FAIL`** is a [RejectionReason](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/entity/RejectionReason.java), except for `ACCOUNT_ACCESS`, which fails with `ACCOUNT_ACCESS_DENIED`. An instrument that doesn't exist fails `TRADABLE` with `NOT_TRADABLE`.
- **Refused account access** is audited against the caller: `client_id` is theirs, `account_id` is empty, and the account they asked for is in `SUBMITTED`'s `requestedAccountId`.
- **Not audited:** a request refused before placement runs, because nobody is logged in (401) or the body is malformed (400 with `errors`). No order was submitted.

A BUY refused for lack of cash:

| `action` | `details` |
|---|---|
| `SUBMITTED` | `{"side": "BUY", "quantity": 100, "price": 250.1235, "instrumentId": 5}` |
| `RULE_CHECKED` | `{"rule": "ACCOUNT_ACCESS", "result": "PASS"}` |
| … | one per rule, in order |
| `RULE_CHECKED` | `{"rule": "CASH", "result": "FAIL", "reason": "INSUFFICIENT_CASH"}` |

**Reading a trail.** Use `findByOrderIdOrderByOccurredAtAsc` for an order, which returns its submission events followed by the rest of its life. Use `findByRequestIdOrderByAuditIdAsc` for a refused order, which has no order ID. Order an order's trail by `created_at`: the seeded checks ([C3](C3-seed-data.md)) were added after their orders' later events, so `audit_id` does not order them.

## Rules

- **Written in the same transaction as the change it describes.** `AuditRecorder.record` and `recordSubmission` are `@Transactional(propagation = MANDATORY)`, so calling them outside a transaction fails, and the event commits or rolls back with the change. An accepted order and its submission events are one transaction.
- **A refused order's trail commits on its own.** There is no order to commit with, so [SubmissionRecorder](../services/buy-sell-service/src/main/java/com/lemarketjames/orders/submission/SubmissionRecorder.java) writes it in a transaction of its own, before the caller is told the order was refused. If the trail can't be written, the caller gets an error, not the refusal.
- **Nothing lost, nothing duplicated.** A submission's events are collected while the checks run and written in one transaction, so a restart or failed deployment part-way through leaves the whole trail or none of it. An event exists exactly when the caller could have received a response. `event_key` is unique, so writing a submission's events a second time fails as a whole and stores nothing, including the order.
- **Written only through `AuditRecorder`.** Never insert into `audit_log` directly. `order_events` from 001 is an unused early draft; don't write to it.
- **Never changed, never deleted.** No feature or API can update or delete an audit event. `AuditEventRepository` offers only insert and the two reads above, and `AuditEventEntity` is immutable, so there is nothing for a feature to call. Don't add a delete or update method to either, and don't widen the repository to `JpaRepository` or `CrudRepository`. The database enforces the same rule on its own; see [The lockdown](#the-lockdown).
- **Each service audits what it does:**

  | Where | Event types |
  |---|---|
  | buy-sell-service, order placement | `SUBMITTED`, then `RULE_CHECKED` for each rule that runs, then `VALIDATED` if the order is saved |
  | buy-sell-service, status changes | `ACCEPTED`, `FILLED`, `REJECTED` |
  | holdings-service, settlement | `SETTLED` |

  Holdings commits `SETTLED` with the cash/share changes and its idempotency receipt. Buy-sell
  then commits `FILLED` and the final order status. These are separate transactions: after a lost
  response, `SETTLED` can temporarily exist without `FILLED`. The persisted execution intent is
  retried with the same price; holdings returns its original receipt without moving funds again,
  and buy-sell records `FILLED` once. Thus `SETTLED` precedes `FILLED` in the completed audit trail.
- **Kept, not deleted.** Events older than the online retention window ([C5](C5-config.md)) count as archived and stay in the table. Nothing is written to the row to say so, because a row is never updated: an event is archived when its `created_at` is older than the window. If archival ever needs to record more than that, it goes in a table of its own. The archival job and the audit views are later stories. Their API is planned in [C6](C6-api.md#planned-agreed-not-built).

## The lockdown

The database refuses a change to an audit record by itself, whatever the application does ([016](../database/schema/016_audit_lockdown.sql)). It covers `audit_log` and the unused `order_events`.

| Layer | Stops | How it fails |
|---|---|---|
| The application account `lemarket_app` holds only `SELECT` and `INSERT` on the audit tables, and owns nothing | Anyone logged in as `lemarket_app` | `permission denied`, SQLSTATE `42501` |
| Triggers refuse `UPDATE`, `DELETE` and `TRUNCATE` for every role | The owner `lemarket`: a migration, a script, a mistaken grant | `Audit records are immutable`, SQLSTATE `LM001` |

- **Two accounts.** `lemarket` owns the schema and runs migrations, seeding and resets. `lemarket_app` is the restricted account for the services. See [database/README.md](../database/README.md#016--audit-lockdown).
- **Migrations may not rewrite audit records either.** A new migration that updates or deletes existing audit rows fails on the trigger. Add rows; don't change them.
- **A new audit table needs its own lockdown.** New tables get ordinary read and write for `lemarket_app` by default. A table that holds audit records must revoke `UPDATE` and `DELETE` and get the same triggers, in the migration that creates it.
- **Resetting test data.** A whole-database reset (`docker compose down -v`, `setup-db.ps1 -Reset`) is unaffected. A test that must remove its own audit events does it through [AuditTestCleanup](../services/buy-sell-service/src/test/java/com/lemarketjames/orders/AuditTestCleanup.java): as the owner, with the trigger switched off inside the deleting transaction. Don't delete audit rows any other way, and never outside a disposable database.
- **Proven by** [AuditLockdownIntegrationTest](../services/buy-sell-service/src/test/java/com/lemarketjames/orders/AuditLockdownIntegrationTest.java), which runs on PostgreSQL only.

## Mirrors (change together with `AuditEventType`)

- The `audit_log.action` CHECK constraint, in both [010](../database/schema/010_shared_contracts.sql) and [014](../database/schema/014_submission_audit.sql): 010 is re-applied before 014, so the two lists must match.
- The seeded events in [011](../database/schema/011_seed_test_data.sql), brought to the current shape by [015](../database/schema/015_seed_submission_audit.sql). 015 also mirrors `ValidationRule`.
