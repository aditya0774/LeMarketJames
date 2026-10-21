#!/bin/sh
# BR-14: takes one backup of the lemarket database and keeps the newest few (database/README.md,
# "Backups and restore"). The nightly db-backup service in docker-compose.yml runs this same file.
#
# Usage: scripts/backup-db.sh [directory]     (default: $BACKUP_DIR, else ./backups)
#
# How it reaches PostgreSQL:
#   - PGHOST set (the db-backup service, or a machine with its own PostgreSQL client): pg_dump here,
#     with the usual PG* variables (PGUSER defaults to the owner, lemarket; PGPASSWORD as needed).
#   - otherwise: pg_dump inside the Compose db container, so a Mac or Linux dev machine needs no
#     PostgreSQL client. Run from the repo root, with the stack up.
#
# The dump is pg_dump's custom format: compressed, and pg_restore can restore all of it or one table.
# It is written under a temporary name and renamed when complete, so a failed run never counts as a
# backup and never causes a good one to be pruned.
#
# POSIX sh, not bash: the db-backup service runs it in the postgres alpine image, which has no bash.
set -eu

dir=${1:-${BACKUP_DIR:-backups}}
keep=${BACKUP_KEEP:-7}
database=${PGDATABASE:-lemarket}
owner=${PGUSER:-lemarket}

case $keep in
    ''|*[!0-9]*|0) echo "BACKUP_KEEP must be a whole number of 1 or more, was '$keep'" >&2; exit 2 ;;
esac

mkdir -p "$dir"
# UTC, so names sort in time order and mean the same on every machine.
file="$dir/lemarket-$(date -u +%Y%m%d-%H%M%S).dump"
partial="$file.partial"
trap 'rm -f -- "$partial"' EXIT

if [ -n "${PGHOST:-}" ]; then
    pg_dump --format=custom --username="$owner" --dbname="$database" --file="$partial"
else
    # Inside the container the owner connects over the local socket, which needs no password.
    docker compose exec -T db pg_dump --format=custom --username="$owner" --dbname="$database" > "$partial"
fi
mv -- "$partial" "$file"

# Keep the newest $keep backups. The names sort by time, so the oldest come last in reverse order.
ls -1 "$dir"/lemarket-*.dump | sort -r | tail -n +"$((keep + 1))" | while IFS= read -r old; do
    rm -f -- "$old"
    echo "Removed old backup $old"
done

echo "Backed up $database to $file ($(du -h "$file" | cut -f1)); keeping the newest $keep."
