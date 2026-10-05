#!/usr/bin/env bash
# LMKT-100: an audit record can't be changed or deleted, and every refused attempt is recorded in
# PostgreSQL's server log with who tried and what they ran (contracts/C2-audit.md).
# Runs only against the disposable Jenkins Compose stack.
set -euo pipefail

if docker compose version >/dev/null 2>&1; then
    compose() { docker compose "$@"; }
else
    compose() { docker-compose "$@"; }
fi

scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT

# psql inside the db container as the given account; connections from inside it need no password.
db() {
    local user=$1
    shift
    compose exec -T db psql -v ON_ERROR_STOP=1 -At -U "$user" -d lemarket "$@"
}

# Marks this run's statements, so their log lines can be told from any other run's.
run="lmkt100-$(date +%s)-$$"

# The records that exist now. The services keep adding events while this runs, so later ones are
# left out: these must come through byte for byte.
last_id=$(db lemarket -c "SELECT max(audit_id) FROM audit_log;")
[[ "$last_id" =~ ^[0-9]+$ ]]
audit_id=$(db lemarket -c "SELECT min(audit_id) FROM audit_log;")
fingerprint() {
    db lemarket -c "SELECT (SELECT count(*) || ':' || md5(string_agg(l::text, '|' ORDER BY audit_id)) FROM audit_log l WHERE audit_id <= $last_id)
        || ' ' || (SELECT count(*) || ':' || coalesce(md5(string_agg(e::text, '|' ORDER BY event_id)), '') FROM order_events e);"
}
before=$(fingerprint)

# Tries one statement and requires it to be refused for the expected reason. It runs in a
# transaction that is rolled back, so an attempt that wrongly got through would still change nothing.
refused() {
    local user=$1 expected=$2 label=$3 sql=$4
    if db "$user" -c "BEGIN" -c "$sql /* $run $label */" -c "ROLLBACK" > "$scratch/attempt.log" 2>&1; then
        echo "NOT REFUSED as $user: $sql" >&2
        return 1
    fi
    if ! grep -qF "$expected" "$scratch/attempt.log"; then
        echo "Refused as $user, but not with '$expected': $sql" >&2
        cat "$scratch/attempt.log" >&2
        return 1
    fi
}

# The record of a refusal: an ERROR line naming the account, and the statement it ran. Both carry
# the backend's process id, which ties them together (a trigger's refusal has HINT and CONTEXT
# lines in between).
recorded() {
    local user=$1 expected=$2 label=$3 statement pid
    statement=$(grep -F "/* $run $label */" "$scratch/db.log" | grep -F "STATEMENT:" | grep -F "user=$user " | tail -n 1) || return 1
    pid=$(sed -E 's/.*\[([0-9]+)\] user=.*/\1/' <<< "$statement")
    grep -F "[$pid] user=$user " "$scratch/db.log" | grep -F "ERROR:" | grep -qF "$expected"
}

denied="permission denied for table"
immutable="Audit records are immutable:"
# account | how it must fail | label | statement
attempts=(
    "lemarket_app|$denied audit_log|app-update|UPDATE audit_log SET archived = TRUE WHERE audit_id = $audit_id"
    "lemarket_app|$denied audit_log|app-delete|DELETE FROM audit_log WHERE audit_id = $audit_id"
    "lemarket_app|$denied audit_log|app-truncate|TRUNCATE audit_log"
    "lemarket_app|$denied order_events|app-events|DELETE FROM order_events"
    "lemarket_app|must be owner of table audit_log|app-disable|ALTER TABLE audit_log DISABLE TRIGGER audit_log_immutable_row"
    "lemarket|$immutable UPDATE on audit_log refused|owner-update|UPDATE audit_log SET archived = TRUE WHERE audit_id = $audit_id"
    "lemarket|$immutable DELETE on audit_log refused|owner-delete|DELETE FROM audit_log WHERE audit_id = $audit_id"
    "lemarket|$immutable TRUNCATE on audit_log refused|owner-truncate|TRUNCATE audit_log"
    "lemarket|$immutable TRUNCATE on order_events refused|owner-events|TRUNCATE order_events"
)

for attempt in "${attempts[@]}"; do
    IFS='|' read -r user expected label sql <<< "$attempt"
    refused "$user" "$expected" "$label" "$sql"
done
echo "All ${#attempts[@]} attempts to change audit records were refused."

# Only the owner can switch a trigger off, and that is recorded as well. Rolled back, so it never
# takes effect.
db lemarket -c "BEGIN" -c "ALTER TABLE audit_log DISABLE TRIGGER audit_log_immutable_row /* $run owner-disable */" -c "ROLLBACK" >/dev/null

after=$(fingerprint)
if [ "$before" != "$after" ]; then
    echo "Audit records changed: $before -> $after" >&2
    exit 1
fi
echo "Audit records are unchanged."

# The server writes its log on its own, so give the last lines a moment to arrive.
missing=""
for wait in $(seq 1 10); do
    compose logs --no-color db > "$scratch/db.log" 2>&1
    missing=""
    for attempt in "${attempts[@]}"; do
        IFS='|' read -r user expected label sql <<< "$attempt"
        recorded "$user" "$expected" "$label" || missing="$missing $label"
    done
    grep -F "/* $run owner-disable */" "$scratch/db.log" | grep -F "user=lemarket " | grep -qF "statement:" \
        || missing="$missing owner-disable"
    [ -z "$missing" ] && break
    sleep 1
done
if [ -n "$missing" ]; then
    echo "Not recorded in the server log:$missing" >&2
    grep -F "$run" "$scratch/db.log" >&2 || true
    exit 1
fi
echo "Every attempt is recorded in the server log with the account and the statement:"
grep -F "$run" "$scratch/db.log" | grep -F "STATEMENT:"
