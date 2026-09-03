# database

Raw SQL, applied manually (no migration tool). `schema/` holds numbered, ordered SQL files (`001_core_schema.sql`, `002_...`) that together define the current database structure — apply them in order against the `paysprint` Postgres database. Add new numbered files here for future schema changes rather than editing old ones in place.
