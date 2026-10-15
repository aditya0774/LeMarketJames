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
| `seed_expired@seed.lemarket.com` | `EXPIRED` | Login is refused, like a closed account (LMKT-57); the history of an account whose documents lapsed |
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

## Segment report proof of concept

[020_segment_report_demo_clients.sql](../database/schema/020_segment_report_demo_clients.sql)
adds seven fictional clients named `demo_segment_1` through `demo_segment_7`.
Their emails are their usernames followed by `@seed.lemarket.com`; they reuse the seed
password above. Their segment attributes come from the existing seed personas. Reapplying
the migration leaves existing balances and clients alone.

Run `python scripts/windows/seed-segment-report.py` against the running local trading stack
after applying 020. The script defines the activity targets and order sizes, uses current
quotes, and places buy/sell pairs through the trading API. This creates real persisted
orders, settlement receipts and audit trails through the normal service writers.
An open market and healthy quote feed are required. Run only one copy at a time.

The script resumes each dedicated demo account to its target rather than duplicating
completed trades. Dates are the actual execution dates; choose a report range including
the day it ran. Existing seed and user activity is included alongside these demo trades.

### Historical client-segment demo (2026)

[021](../database/schema/021_historical_segment_demo_clients.sql) adds fictional
`demo_history_01` through `demo_history_30` clients, with emails formed like the other
seed logins and the same password. Their segments are inherited from the existing
seed personas. Client counts, opening deposits and registration dates live in 021.

After applying 021, run `scripts/windows/seed-historical-segment-report.ps1 -Apply`.
Without `-Apply`, the script previews and rolls back the history. The standalone
[fixture writer](../scripts/seed/HistoricalSegmentSeed.java) owns the month-by-month
activity plan, synthetic prices and sizes. It creates June-September 2026 history,
including June and July comparisons with meaningful nonzero baselines. Retail
clients have smaller orders, active traders trade more often, and high-net-worth
clients place larger orders. Some clients enter or leave the monthly active cohort.

Orders are explicitly marked with a synthetic quote source. Each buy is followed
by a sell of the same shares, with modest gains or losses. Cash, holdings and
settlement receipts are written atomically, and audit events go through the existing
`AuditRecorder` with a fixture clock; no audit record is rewritten. This is an offline
historical fixture, not actual historical market execution. It does not publish Kafka
events, so notification, surveillance and market-activity consumer tables are not seeded.

The entire history commits once under an advisory lock. Reruns verify existing
history and leave it unchanged; existing clients outside this dedicated set are untouched.
Compare July 1-31 with June 1-30 using custom comparison dates (calendar months differ
in length). The report also includes any earlier seed or user trades in those ranges.
