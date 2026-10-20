-- 025: Personal watchlist (S41, LMKT-131, contracts/C6-api.md)
--
-- The stocks a client keeps an eye on. One row per account and stock, so adding the same stock
-- twice is refused by the database even when two requests race. Prices are not stored: the
-- trading app shows each watched stock's live quote from the quotes it already polls.
--
-- IDEMPOTENT: safe to run more than once (Jenkins re-applies every file from 004 on).

BEGIN;

CREATE TABLE IF NOT EXISTS watchlist (
    watchlist_id  SERIAL PRIMARY KEY,
    account_id    INTEGER NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),
    added_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT watchlist_account_instrument_uq UNIQUE (account_id, instrument_id)
);

-- The list is always read for one account, newest first.
CREATE INDEX IF NOT EXISTS watchlist_account_added_idx
    ON watchlist (account_id, added_at DESC);

COMMIT;
