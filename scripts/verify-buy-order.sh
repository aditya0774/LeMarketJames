#!/usr/bin/env bash
# Runs only against the disposable Jenkins Compose stack, through the frontend proxy.
set -euo pipefail

if docker compose version >/dev/null 2>&1; then
    compose() { docker compose "$@"; }
else
    compose() { docker-compose "$@"; }
fi

base=http://localhost:4200
username="buyci$(date +%s)"
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT

wait_for_backend() {
    for attempt in $(seq 1 60); do
        if curl --fail --silent http://localhost:8085/actuator/health >/dev/null; then
            return 0
        fi
        sleep 1
    done
    echo "Backend did not become healthy" >&2
    return 1
}

wait_for_backend
curl --fail --silent --show-error "$base/api/auth/register" \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"email\":\"$username@example.com\",\"password\":\"Pass123!\",\"fullName\":\"Buy CI\",\"streetAddress\":\"123 Main St\",\"city\":\"Boston\",\"state\":\"MA\",\"zipCode\":\"02110\",\"country\":\"US\",\"ssn\":\"123-45-6789\",\"initialDeposit\":10000,\"investmentExperience\":\"beginner\",\"employmentStatus\":\"employed\",\"dateOfBirth\":\"1990-01-01\",\"phoneNumber\":\"5551234567\",\"termsAccepted\":true}" >/dev/null
curl --fail --silent --show-error "$base/api/auth/login" \
    -c "$scratch/cookies" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username@example.com\",\"password\":\"Pass123!\"}" > "$scratch/login.json"
account_id=$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).accountId' "$scratch/login.json")
[[ "$account_id" =~ ^[0-9]+$ ]]
instrument_id=$(compose exec -T db psql -At -U lemarket -d lemarket -c "SELECT instrument_id FROM instruments WHERE ticker='AAPL';")
[[ "$instrument_id" =~ ^[0-9]+$ ]]

status=$(curl --silent --show-error -o "$scratch/order.json" -w '%{http_code}' \
    "$base/api/v1/orders" -b "$scratch/cookies" -H 'Content-Type: application/json' \
    --data "{\"accountId\":$account_id,\"instrumentId\":$instrument_id,\"orderType\":\"BUY\",\"quantity\":1}")
test "$status" = 201
order_id=$(node -e 'const o=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")); if(!o.success || o.orderStatus!=="SUBMITTED" || !(o.pricePerUnit>0) || !Number.isInteger(o.orderId)) process.exit(1); console.log(o.orderId);' "$scratch/order.json")
[[ "$order_id" =~ ^[0-9]+$ ]]
# Wait for automatic execution before comparing immutable restart state.
filled=0
for attempt in $(seq 1 60); do
    curl --fail --silent --show-error "$base/api/v1/orders/$order_id" -b "$scratch/cookies" > "$scratch/final.json"
    if node -e 'const o=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")); process.exit(o.orderStatus==="FILLED"?0:1)' "$scratch/final.json"; then
        filled=1
        cp "$scratch/final.json" "$scratch/order.json"
        break
    fi
    sleep 1
done
test "$filled" = 1
# LMKT-23 AC3: the fill was announced. Until a broker exists the Kafka stub's log line is the only
# place an OrderFilled event shows outside the service, so that is where it is checked. Forwarding
# runs just after the fill commits, hence the short wait.
announced=0
for attempt in $(seq 1 10); do
    if compose logs --no-color buy-sell-service \
        | grep -F "topic=lemarket.orders.filled key=$order_id event=OrderFilled[" > "$scratch/filled-event.log"; then
        announced=1
        break
    fi
    sleep 1
done
test "$announced" = 1
# The event carries the quote used: where it came from and when the feed produced it.
grep -Eq 'quoteSource=[A-Z_]+, quoteTime=[0-9]{4}-' "$scratch/filled-event.log"
cash_before=$(compose exec -T db psql -At -U lemarket -d lemarket -c "SELECT cash_balance FROM accounts WHERE account_id=$account_id;")
count=$(compose exec -T db psql -At -U lemarket -d lemarket \
    -c "SELECT count(*) FROM orders WHERE order_id=$order_id AND account_id=$account_id AND order_type='BUY' AND order_status='FILLED' AND price_per_unit>0;")
test "$count" = 1

# Restart the application, retaining PostgreSQL and its named volume.
compose restart buy-sell-service
wait_for_backend
curl --fail --silent --show-error "$base/api/v1/orders/$order_id" -b "$scratch/cookies" > "$scratch/readback.json"
node -e 'const fs=require("fs"), a=JSON.parse(fs.readFileSync(process.argv[1],"utf8")), b=JSON.parse(fs.readFileSync(process.argv[2],"utf8")); for(const key of ["orderId","accountId","instrumentId","orderType","quantity","pricePerUnit","orderStatus"]) if(a[key]!==b[key]) throw Error("Restart readback mismatch: "+key);' "$scratch/order.json" "$scratch/readback.json"
echo "BUY order persisted and remained readable after buy-sell-service restart."

cash_after=$(compose exec -T db psql -At -U lemarket -d lemarket -c "SELECT cash_balance FROM accounts WHERE account_id=$account_id;")
test "$cash_before" = "$cash_after"
receipts=$(compose exec -T db psql -At -U lemarket -d lemarket -c "SELECT count(*) FROM settlement_receipts WHERE order_id=$order_id AND rejection_reason IS NULL;")
test "$receipts" = 1
