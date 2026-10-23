# LEAP demo commands for the Mac (Docker stack). Load once per terminal:   source ~/LeMarketJames/demo/demo.sh
# See demo/README.md for the whole set-up.
# Then type the short names below (demo_help lists them). Nothing here runs until you call it.

# This file lives in the repo's demo folder, so the repo is the folder above it (LMJ_REPO overrides).
REPO=${LMJ_REPO:-${${(%):-%x}:A:h:h}}
APP=https://localhost:4200
ACC_A=49                                  # Client A, Lebron@lemarket.com
DEMO_DIR=~/lmj-demo
mkdir -p "$DEMO_DIR"

# Demo settings: every docker compose command below inherits them.
export SIM_RESPECT_MARKET_HOURS=false SESSION_INACTIVITY_TIMEOUT=PT5M
if [ -f "$REPO/.env" ]; then set -a; . "$REPO/.env"; set +a; fi
T="X-Internal-Token: ${INTERNAL_API_TOKEN:-change-this-development-only-internal-token}"

dc()      { (cd "$REPO" && docker compose "$@"); }
sqlo()    { docker exec -i lemarketjames-db-1 psql -U lemarket -d lemarket -c "$1"; }
json()    { curl -s -H "$T" -H 'Content-Type: application/json' "$@"; echo; }
code()    { curl -sk -o /dev/null -w '%{http_code}' -H "Cookie: jwt=$1" "$APP/api/v1/holdings"; }
wait_up() { local p=${1:-8085}; until curl -s "localhost:$p/actuator/health" | grep -q UP; do sleep 2; done; echo "UP ($p)"; }

# ---- Stack -------------------------------------------------------------------
start_stack() { dc up -d && for p in 8082 8084 8085 8086; do wait_up $p; done; }
health()      { for p in 8082 8084 8085 8086; do printf '%s ' "$p"; curl -s "localhost:$p/actuator/health"; echo; done; }
kafka_feed()  { docker exec lemarketjames-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
                  --bootstrap-server localhost:9092 --include 'lemarket\.orders\..*' 2>/dev/null; }
logs()        { dc logs -f --tail=0 orders-service trading-service; }

# ---- Evidence queries --------------------------------------------------------
totals() { sqlo "SELECT a.account_id, a.cash_balance,
  (SELECT count(*) FROM orders o WHERE o.account_id = a.account_id AND o.order_status = 'FILLED') AS filled_orders,
  (SELECT count(*) FROM orders o WHERE o.account_id = a.account_id) AS all_orders,
  (SELECT string_agg(i.ticker || ' ' || h.quantity, ', ' ORDER BY i.ticker)
     FROM holdings h JOIN instruments i USING (instrument_id)
     WHERE h.account_id = a.account_id AND h.quantity > 0) AS holdings
  FROM accounts a WHERE a.account_id = $ACC_A;"; }
last_order() { sqlo "SELECT order_id, order_type, quantity, order_status, rejection_reason, submitted_at, filled_at
  FROM orders WHERE account_id = $ACC_A ORDER BY order_id DESC LIMIT ${1:-1};"; }
trace() {
  [ -z "$1" ] && { echo "usage: trace ORDER_ID"; return 1; }
  sqlo "SELECT order_id, order_type, quantity, price_per_unit, order_status, rejection_reason,
    quote_source, quote_time, submitted_at, accepted_at, filled_at FROM orders WHERE order_id = $1;"
  sqlo "SELECT created_at, action, details FROM audit_log WHERE order_id = $1 ORDER BY created_at, audit_id;"; }
try_change() {   # act 3.4: both are refused, even for the database owner
  [ -z "$1" ] && { echo "usage: try_change ORDER_ID"; return 1; }
  sqlo "UPDATE orders SET quantity = quantity + 1 WHERE order_id = $1;"
  sqlo "DELETE FROM audit_log WHERE order_id = $1;"; }
reports() {      # act 5.3: the nightly summary
  sqlo "SELECT time_zone, summarized_through, refreshed_at FROM reporting_summary_state;"
  sqlo "SELECT trade_date, symbol, side, trade_count, gross_amount FROM reporting_daily_trades
    ORDER BY trade_date DESC, gross_amount DESC LIMIT 8;"; }
backups() { dc logs --tail=5 db-backup; }

run_overnight_now() {  # prep step 5, only after a database rebuild: tonight's overnight report, now
  # The service never re-runs a night it already recorded, so clear yesterday's "did not run" first.
  sqlo "DELETE FROM report_runs WHERE run_date = (now() AT TIME ZONE 'America/New_York')::date - 1
          AND status = 'FAILED' AND started_at IS NULL;"
  local f="$DEMO_DIR/overnight-now.yml" files="-f docker-compose.yml"
  [ -f "$REPO/docker-compose.override.yml" ] && files="$files -f docker-compose.override.yml"
  printf 'services:\n  staff-ops-service:\n    environment:\n      LMJ_REPORTS_OVERNIGHTRUNTIME: "%s"\n      LMJ_REPORTS_READYBY: "23:59"\n' \
    "$(TZ=America/New_York date -v-10M +%H:%M)" > "$f"
  (cd "$REPO" && docker compose ${=files} -f "$f" up -d --no-deps staff-ops-service) && wait_up 8086
  sleep 15
  sqlo "SELECT report_name, run_date, status, ready_at, failure_reason FROM report_runs ORDER BY run_date DESC LIMIT 3;"
  dc up -d --no-deps staff-ops-service && wait_up 8086; }   # back to the normal 2 a.m. schedule

# ---- Act 4 failure tests ----------------------------------------------------
exec_pause()  { json -X PUT localhost:8085/internal/execution/control -d '{"enabled": false}'; }
exec_resume() { json -X PUT localhost:8085/internal/execution/control -d '{"enabled": true}'; }
restart_orders() { dc restart orders-service && wait_up 8085; }

bad_deploy()    { APP_DB_PASSWORD=wrong-password dc up -d --no-deps orders-service; }
deploy_status() { dc ps -a orders-service; dc logs orders-service | grep -m1 "password authentication failed"; }
rollback()      { dc up -d --no-deps orders-service && wait_up 8085; }

capture_old() {  # act 0.1: a token that will have expired by act 4
  curl -sk -c "$DEMO_DIR/old.jar" -H 'Content-Type: application/json' \
    -d '{"username":"seed_active@seed.lemarket.com","password":"Pass123!"}' "$APP/api/auth/login" > /dev/null
  OLD=$(awk '$6=="jwt"{print $7}' "$DEMO_DIR/old.jar"); date +%T > "$DEMO_DIR/old.time"
  echo "captured at $(cat "$DEMO_DIR/old.time"): $(code "$OLD") (valid now)"; }
tokens() {       # act 4.3
  OLD=$(awk '$6=="jwt"{print $7}' "$DEMO_DIR/old.jar" 2>/dev/null)
  [ -n "$OLD" ] && echo "expired  (captured $(cat "$DEMO_DIR/old.time"), unused since): $(code "$OLD")"
  curl -sk -c "$DEMO_DIR/new.jar" -H 'Content-Type: application/json' \
    -d '{"username":"seed_trader@seed.lemarket.com","password":"Pass123!"}' "$APP/api/auth/login" > /dev/null
  local NEW BAD c; NEW=$(awk '$6=="jwt"{print $7}' "$DEMO_DIR/new.jar")
  c=${NEW:30:1}; if [ "$c" = A ]; then c=B; else c=A; fi
  BAD="${NEW:0:30}${c}${NEW:31}"
  echo "valid    (fresh sign-in):                       $(code "$NEW")"
  echo "tampered (one character changed in the middle): $(code "$BAD")"
  curl -sk -X POST -H "Cookie: jwt=$NEW" "$APP/api/auth/logout" > /dev/null
  echo "revoked  (same valid token after signing out):  $(code "$NEW")"; }

fault_on()    { json -X PUT    "localhost:8084/internal/holdings/control/settlement-faults/$ACC_A"; }
fault_check() { json           "localhost:8084/internal/holdings/control/settlement-faults/$ACC_A"; }
fault_off()   { json -X DELETE "localhost:8084/internal/holdings/control/settlement-faults/$ACC_A"; }

feed_down()   { json -X PUT  localhost:8084/internal/market/control/feed -d '{"mode": "UNAVAILABLE"}'; }
feed_stale()  { json -X PUT  localhost:8084/internal/market/control/feed -d '{"mode": "STALE"}'; }
feed_reset()  { json -X POST localhost:8084/internal/market/control/reset; }

all_clear() {    # Appendix B: put every test switch back to normal
  feed_reset; exec_resume; fault_off; health; }

demo_help() { cat <<'EOF'
Stack:     start_stack  health  kafka_feed  logs
Evidence:  totals  last_order [N]  trace ID  try_change ID  reports  backups
Prep:      run_overnight_now   (only after a database rebuild)
Act 0:     capture_old
Act 4.1:   exec_pause  (buy 1 share)  last_order  restart_orders
Act 4.2:   bad_deploy ... deploy_status  rollback
Act 4.3:   tokens
Act 4.4:   fault_on  (buy 2 shares)  totals  fault_check  fault_off
Act 4.5:   feed_down  (try a buy)  feed_reset
Rescue:    all_clear
EOF
}
echo "LEAP demo commands loaded (Client A = account $ACC_A). Type demo_help for the list."
