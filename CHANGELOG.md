# Changelog

## Unreleased

- LMKT-91: Add optional `date` and `timeZone` filters to account order history and
  status-filtered history, using local calendar boundaries and UTC placement times.
  Preserve array responses, ownership checks, and unfiltered requests.
- New order entity timestamps use UTC explicitly. Existing non-UTC timestamp data
  requires source-zone-aware conversion; frontend wiring remains LMKT-92.
