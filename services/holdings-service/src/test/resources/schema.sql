-- H2-only: mirrors just enough of database/schema/001_core_schema.sql's `instruments` table so
-- HoldingsOwnDataIntegrationTest can insert a real instrument row to hold. Against real Postgres
-- (application-postgres-test.properties, no spring.sql.init.mode) this file is never run — the
-- table already exists there.
CREATE TABLE IF NOT EXISTS instruments (
    instrument_id   SERIAL PRIMARY KEY,
    ticker          VARCHAR(20) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    asset_class     VARCHAR(20) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE,
    location        VARCHAR(50)
);
