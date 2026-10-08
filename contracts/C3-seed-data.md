# C3 – Seed / test data set

Read-side stories (portfolio, audit, reporting) can be built and demoed before the write side exists, because every environment starts with the same people, stocks and history. This page is the only description of the seed personas. The data itself is [011_seed_test_data.sql](../database/schema/011_seed_test_data.sql); [010](../database/schema/010_shared_contracts.sql) adds the suspended stock, and [015](../database/schema/015_seed_submission_audit.sql) adds the submission audit trail to the seeded orders.

## Logging in

Every seed login, client or staff, uses the password **`Pass123!`**. Log in with the email.

## Clients

| Email | Standing | Use it to test |
|---|---|---|
| `seed_active@seed.lemarket.com` | Active, `RETAIL` | The main demo account: 12 months of monthly fills, a closed position from past years, and an open order in every non-final status |
| `seed_trader@seed.lemarket.com` | Active, `ACTIVE_TRADER` | Frequent trading; a second account for segment reports |
| `seed_hnw@seed.lemarket.com` | Active, `HIGH_NET_WORTH` | Large positions and balances |
| `seed_closed@seed.lemarket.com` | `CLOSED` | Login is refused; the history of a closed account |
| `seed_expired@seed.lemarket.com` | `EXPIRED` | Can log in and view, but orders are refused with `ACCOUNT_RESTRICTED` |
| `seed_locked@seed.lemarket.com` | Locked out (lock never expires) | The lockout message, even with the right password |
| `seed_notrading@seed.lemarket.com` | Active, trading disabled on the account | Orders refused with `ACCOUNT_RESTRICTED`; has one such rejected order, and one refused submission in the audit trail |

## Staff

| Email | Role |
|---|---|
| `ops@seed.lemarket.com` | `TRADING_OPS` |
| `analyst@seed.lemarket.com` | `ANALYST` |

## Stocks

The 50 real stocks from 009 are all tradable. **`CAVS`** (id 52) is the suspended one: it has a live quote, but orders are refused with `NOT_TRADABLE`.

## History

- Orders in every status, spread over days, months and years. Dates are relative to when the seed ran, so "last 12 months" stays true.
- Holdings, average costs and cash are calculated from the seeded fills, so every balance agrees with the history.
- Audit events for every seeded order, one per lifecycle step it reached. The filled ones are complete trades: SUBMITTED, a RULE_CHECKED for each placement check, VALIDATED, ACCEPTED, FILLED, SETTLED.
- One refused submission for `seed_notrading`: a trail with no order, ending on the failed `ACCOUNT_STATUS` check ([C2](C2-audit.md#the-submission-trail)). Find it by its request ID, not an order ID.

## Where it's loaded

It loads wherever the schema does:
- Docker, on an empty database volume
- `scripts/windows/setup-db.ps1`
- every Jenkins run.

The file is skipped once `seed_active` exists, so re-running it never duplicates data. To add seed data, put it in a new numbered migration with its own marker check rather than editing 011, which existing databases have already run.
