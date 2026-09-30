# Changelog

## Unreleased

- LMKT-92: Wire dashboard date controls to the real history API with the browser's
  time zone; normalize UTC placement dates, cancel stale requests, and preserve
  account-wide open-order counts. Clearing reloads full history.
- Add Playwright end-to-end coverage using real authentication, services, and seeded
  PostgreSQL orders spanning 2023-2026, leap day, and both daylight-saving transitions.

- LMKT-91: Add optional `date` and `timeZone` filters to account order history and
  status-filtered history, using local calendar boundaries and UTC placement times.
  Preserve array responses, ownership checks, and unfiltered requests.
- New order entity timestamps use UTC explicitly. Existing non-UTC timestamp data
  requires source-zone-aware conversion; frontend wiring remains LMKT-92.
