#!/bin/sh
# BR-14: the db-backup service's loop (docker-compose.yml). Runs backup-db.sh, next to this file,
# once a day at $BACKUP_TIME (HH:MM, UTC; default 02:00, after the US market closes), and once at
# start when the volume holds no backup yet, so a new stack has one straight away.
#
# A failed backup is logged and retried the next night; the loop keeps running, so the container
# stays up and its log shows every run (docker compose logs db-backup).
set -u

dir=${BACKUP_DIR:-/backups}
at=${BACKUP_TIME:-02:00}
script="$(dirname "$0")/backup-db.sh"

backup() {
    sh "$script" "$dir" || echo "Backup failed; the next one runs at $at UTC." >&2
}

# The shell is PID 1 here and ignores SIGTERM unless told otherwise; exit at once on docker stop.
trap 'exit 0' TERM INT

ls "$dir"/lemarket-*.dump > /dev/null 2>&1 || backup

echo "Nightly backups at $at UTC into $dir."
last=""
while :; do
    today=$(date -u +%F)
    if [ "$(date -u +%H:%M)" = "$at" ] && [ "$today" != "$last" ]; then
        backup
        last=$today
    fi
    # In the background, so the trap above runs as soon as a stop arrives.
    sleep 20 &
    wait $!
done
