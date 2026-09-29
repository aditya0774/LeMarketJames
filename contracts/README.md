# Shared contracts (C1–C7)

These seven contracts let front-end, back-end and neighbouring stories be built in parallel: each side codes against the contract instead of waiting for the other. Read the one your change touches **before** coding, and code against it.

| | Contract | What it fixes |
|---|---|---|
| C1 | [Order model and lifecycle](C1-orders.md) | Order statuses, the moves between them, rejection codes |
| C2 | [Audit event format](C2-audit.md) | What gets audited, in what shape, and the same-transaction rule |
| C3 | [Seed / test data set](C3-seed-data.md) | The personas, stocks and history every environment starts with |
| C4 | [Simulated quote feed](C4-quote-feed.md) | Controllable prices, and stale or unavailable feeds on demand |
| C5 | [Configuration settings](C5-config.md) | Where each business setting lives and who reads it |
| C6 | [API and event contracts](C6-api.md) | REST endpoints, internal events, the execution interface, the reporting data source |
| C7 | [Role model](C7-roles.md) | Client vs staff roles, and which role may use which view |

## Rules for these documents

- **One home per fact.** Value lists and defaults (statuses, codes, roles, event types, settings) are defined once, in code, with their meaning in the Javadoc. These documents explain the rules around them and link to that code; they never copy a list or a default. When the code changes, only the link target changes.
- **Mirrors are named.** Where another language has to repeat a Java list (a TypeScript union, a SQL `CHECK`), the contract names each mirror, so they're changed together.
- **Same PR.** A change to a contract's code updates its document in the same pull request.
