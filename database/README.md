# database

Raw SQL, applied manually (no migration tool). `schema/` holds numbered, ordered SQL files (`001_core_schema.sql`, `002_...`) that together define the current database structure — apply them in order against the `lemarket` Postgres database. Add new numbered files here for future schema changes rather than editing old ones in place.

Existing Docker volumes do not rerun initialization scripts. For an existing database
that already has scripts 001–003, apply the SSN hash column update without deleting data:

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket < database/schema/004_widen_ssn_for_hash.sql
```

The command above uses a POSIX shell. In PowerShell:

```powershell
Get-Content database/schema/004_widen_ssn_for_hash.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket
```

Jenkins applies this repeatable update before its HTTP smoke tests. New databases
apply it automatically with the other initialization scripts.
