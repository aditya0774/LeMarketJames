#!/usr/bin/env bash
# Proves the three Kafka consumers on the running stack (contracts/C6-api.md, "The consumers"):
# one large order is placed and filled, and each consumer must then show what it made of that
# order's events, read back the way its users read it:
#
#   notification-service  the client's notifications say the order was FILLED     (trading gateway)
#   surveillance-service  Trading Ops have an alert for the large order           (staff gateway)
#   activity-service      market activity counts the fill for the stock           (trading gateway)
#
# Nothing here writes to the consumers' tables: every row checked below got there from a Kafka
# record that buy-sell-service published. The script ends by printing the broker's own list of
# consumer groups, which shows each service attached to its topic.
#
# Runs only against the disposable Jenkins Compose stack, through the two frontend proxies.
set -euo pipefail

if docker compose version >/dev/null 2>&1; then
    compose() { docker compose "$@"; }
else
    compose() { docker-compose "$@"; }
fi

trading=http://localhost:4200
staff=http://localhost:4201
username="kafkaci$(date +%s)"
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT

# wait_until <what> <command...>: retries the command once a second. A consumer handles an event
# just after the order's transaction commits, so its result appears a moment later, not at once.
wait_until() {
    local what=$1
    shift
    for attempt in $(seq 1 60); do
        if "$@"; then
            return 0
        fi
        sleep 1
    done
    echo "Timed out waiting for $what" >&2
    return 1
}

# json <file> <expression>: whether a JavaScript expression holds for the JSON in the file, where
# `body` is the parsed file and `id` is the order placed below.
json() {
    node -e 'const body = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
             const holds = new Function("body", "id", "return " + process.argv[2]);
             process.exit(holds(body, Number(process.argv[3])) ? 0 : 1);' "$1" "$2" "${order_id:-0}"
}

psql_value() {
    compose exec -T db psql -At -U lemarket -d lemarket -c "$1"
}

for port in 8085 8087 8088 8091; do
    wait_until "the service on port $port" curl --fail --silent --output /dev/null "http://localhost:$port/actuator/health"
done

# --- Trading Ops sign in first: the alerts answer says how many shares make an order large ----
# The seed Trading Ops login (contracts/C3-seed-data.md). Through the staff app's proxy, so the
# staff gateway's route to surveillance-service is part of what is checked.
curl --fail --silent --show-error "$staff/api/auth/login" \
    -c "$scratch/staff-cookies" -H 'Content-Type: application/json' \
    --data '{"username":"ops@seed.lemarket.com","password":"Pass123!"}' >/dev/null
curl --fail --silent --show-error "$staff/api/v1/surveillance/alerts" -b "$scratch/staff-cookies" > "$scratch/alerts.json"
# Read from the service rather than written here, so the script follows the setting (contract C5).
large_quantity=$(node -p 'Math.ceil(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).largeOrderQuantity)' "$scratch/alerts.json")
[[ "$large_quantity" =~ ^[1-9][0-9]*$ ]]
echo "An order of $large_quantity shares or more counts as large."

# --- A client places one large order -----------------------------------------------------------
# The deposit is big enough to buy that many shares of any stock in the market.
curl --fail --silent --show-error "$trading/api/auth/register" \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"email\":\"$username@example.com\",\"password\":\"Pass123!\",\"fullName\":\"Kafka CI\",\"streetAddress\":\"123 Main St\",\"city\":\"Boston\",\"state\":\"MA\",\"zipCode\":\"02110\",\"country\":\"US\",\"ssn\":\"123-45-6789\",\"initialDeposit\":10000000,\"investmentExperience\":\"beginner\",\"employmentStatus\":\"employed\",\"dateOfBirth\":\"1990-01-01\",\"phoneNumber\":\"5551234567\",\"termsAccepted\":true}" >/dev/null
curl --fail --silent --show-error "$trading/api/auth/login" \
    -c "$scratch/cookies" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username@example.com\",\"password\":\"Pass123!\"}" > "$scratch/login.json"
account_id=$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).accountId' "$scratch/login.json")
[[ "$account_id" =~ ^[0-9]+$ ]]
instrument_id=$(psql_value "SELECT instrument_id FROM instruments WHERE ticker='AAPL';")
[[ "$instrument_id" =~ ^[0-9]+$ ]]

status=$(curl --silent --show-error -o "$scratch/order.json" -w '%{http_code}' \
    "$trading/api/v1/orders" -b "$scratch/cookies" -H 'Content-Type: application/json' \
    --data "{\"accountId\":$account_id,\"instrumentId\":$instrument_id,\"orderType\":\"BUY\",\"quantity\":$large_quantity}")
if [ "$status" != 201 ]; then
    echo "Placing the order answered $status:" >&2
    cat "$scratch/order.json" >&2
    exit 1
fi
order_id=$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).orderId' "$scratch/order.json")
[[ "$order_id" =~ ^[0-9]+$ ]]
echo "Placed order $order_id: BUY $large_quantity AAPL for account $account_id."

order_is_filled() {
    curl --fail --silent --show-error "$trading/api/v1/orders/$order_id" -b "$scratch/cookies" > "$scratch/final.json" \
        && json "$scratch/final.json" 'body.orderStatus === "FILLED"'
}
wait_until "order $order_id to fill" order_is_filled

# --- 1. notification-service: the client is told ---------------------------------------------
client_is_notified() {
    curl --fail --silent --show-error "$trading/api/v1/notifications" -b "$scratch/cookies" > "$scratch/notifications.json" \
        && json "$scratch/notifications.json" 'body.success && body.notifications.some(n => n.orderId === id && n.status === "FILLED")'
}
wait_until "the FILLED notification of order $order_id" client_is_notified
echo "notification-service: the client's notifications for order $order_id:"
node -e 'const body = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
         for (const n of body.notifications.filter(n => n.orderId === Number(process.argv[2]))) console.log("  " + n.occurredAt + "  " + n.message);' \
    "$scratch/notifications.json" "$order_id"
# One notification per status change, never two for the same status.
duplicates=$(psql_value "SELECT count(*) FROM (SELECT status FROM notifications WHERE order_id=$order_id GROUP BY status HAVING count(*) > 1) d;")
test "$duplicates" = 0

# --- 2. surveillance-service: Trading Ops are alerted ----------------------------------------
ops_are_alerted() {
    curl --fail --silent --show-error "$staff/api/v1/surveillance/alerts" -b "$scratch/staff-cookies" > "$scratch/alerts.json" \
        && json "$scratch/alerts.json" 'body.success && body.alerts.some(a => a.orderId === id && a.reason === "LARGE_ORDER" && a.side === "BUY")'
}
wait_until "the alert for order $order_id" ops_are_alerted
alerts=$(psql_value "SELECT count(*) FROM order_alerts WHERE order_id=$order_id AND account_id=$account_id AND quantity=$large_quantity;")
test "$alerts" = 1
echo "surveillance-service: Trading Ops have one LARGE_ORDER alert for order $order_id."
# A client must not be able to read alerts: the trading gateway has no route to them, and the
# service itself refuses the role.
client_status=$(curl --silent --output /dev/null --write-out '%{http_code}' "http://localhost:8088/api/v1/surveillance/alerts" -b "$scratch/cookies")
test "$client_status" = 403

# --- 3. activity-service: the fill is in the market's activity -------------------------------
fill_is_counted() {
    curl --fail --silent --show-error "$trading/api/v1/market-activity" -b "$scratch/cookies" > "$scratch/activity.json" \
        && test "$(psql_value "SELECT count(*) FROM trade_activity WHERE order_id=$order_id AND instrument_id=$instrument_id AND quantity=$large_quantity;")" = 1 \
        && INSTRUMENT_ID="$instrument_id" LARGE_QUANTITY="$large_quantity" json "$scratch/activity.json" \
            'body.success && body.activity.some(a => a.instrumentId === Number(process.env.INSTRUMENT_ID) && a.trades >= 1 && a.volume >= Number(process.env.LARGE_QUANTITY))'
}
wait_until "the fill of order $order_id in market activity" fill_is_counted
echo "activity-service: market activity for AAPL over the last $(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).windowHours' "$scratch/activity.json") hours:"
node -e 'const body = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
         console.log("  " + JSON.stringify(body.activity.find(a => a.instrumentId === Number(process.argv[2]))));' \
    "$scratch/activity.json" "$instrument_id"

# --- The broker's own view: three consumer groups, each on its topic -------------------------
compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
    --describe --all-groups > "$scratch/groups.txt" 2>/dev/null
echo "Kafka consumer groups (GROUP, TOPIC, PARTITION, CURRENT-OFFSET, LOG-END-OFFSET, LAG, ...):"
cat "$scratch/groups.txt"
grep -Eq '^notification-service +lemarket\.orders\.status-changed ' "$scratch/groups.txt"
grep -Eq '^surveillance-service +lemarket\.orders\.submitted ' "$scratch/groups.txt"
grep -Eq '^activity-service +lemarket\.orders\.filled ' "$scratch/groups.txt"

echo "All three Kafka consumers handled order $order_id."
