#!/usr/bin/env bash
# Disposable Jenkins stack only. The HTTP requests traverse frontend -> gateway -> services.
set -euo pipefail
if docker compose version >/dev/null 2>&1; then
    compose() { docker compose "$@"; }
else
    compose() { docker-compose "$@"; }
fi
base=http://localhost:4200
username="sellci$(date +%s)"
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT

# Keep credentials out of shell tracing, including when Jenkins invokes this script.
set +x
curl --fail --silent --show-error "$base/api/auth/register" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"email\":\"$username@example.com\",\"password\":\"Pass123!\",\"fullName\":\"Sell CI\",\"streetAddress\":\"123 Main St\",\"city\":\"Boston\",\"state\":\"MA\",\"zipCode\":\"02110\",\"country\":\"US\",\"ssn\":\"123-45-6789\",\"initialDeposit\":500,\"investmentExperience\":\"beginner\",\"employmentStatus\":\"employed\",\"dateOfBirth\":\"1990-01-01\",\"phoneNumber\":\"5551234567\",\"termsAccepted\":true}" >/dev/null
curl --fail --silent --show-error "$base/api/auth/login" -c "$scratch/cookies" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username@example.com\",\"password\":\"Pass123!\"}" > "$scratch/login.json"
account_id=$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).accountId' "$scratch/login.json")
instrument_id=$(compose exec -T db psql -At -U lemarket -d lemarket -c "SELECT instrument_id FROM instruments WHERE ticker='AAPL';")
[[ "$account_id" =~ ^[0-9]+$ && "$instrument_id" =~ ^[0-9]+$ ]]
# Submission does not execute buys; seed this test user's existing position explicitly.
compose exec -T db psql -v ON_ERROR_STOP=1 -U lemarket -d lemarket \
    -c "INSERT INTO holdings(account_id,instrument_id,quantity,last_updated) VALUES ($account_id,$instrument_id,3,CURRENT_TIMESTAMP);" >/dev/null
status=$(curl --silent --show-error -o "$scratch/order.json" -w '%{http_code}' \
    "$base/api/v1/orders" -b "$scratch/cookies" -H 'Content-Type: application/json' \
    --data "{\"accountId\":$account_id,\"instrumentId\":$instrument_id,\"orderType\":\"SELL\",\"quantity\":1}")
test "$status" = 201
order_id=$(node -e 'const o=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")); if(!o.success || o.orderType!=="SELL" || o.orderStatus!=="SUBMITTED" || !Number.isInteger(o.orderId)) process.exit(1); console.log(o.orderId);' "$scratch/order.json")
[[ "$order_id" =~ ^[0-9]+$ ]]
count=$(compose exec -T db psql -At -U lemarket -d lemarket \
    -c "SELECT count(*) FROM orders WHERE order_id=$order_id AND account_id=$account_id AND instrument_id=$instrument_id AND order_type='SELL' AND quantity=1 AND order_status='SUBMITTED';")
test "$count" = 1
compose restart buy-sell-service
ready=0
for attempt in $(seq 1 60); do
    if curl --fail --silent http://localhost:8085/actuator/health >/dev/null; then
        ready=1
        break
    fi
    sleep 1
done
test "$ready" = 1
curl --fail --silent --show-error "$base/api/v1/orders/$order_id" -b "$scratch/cookies" > "$scratch/readback.json"
node -e 'const fs=require("fs"), a=JSON.parse(fs.readFileSync(process.argv[1],"utf8")), b=JSON.parse(fs.readFileSync(process.argv[2],"utf8")); for(const key of ["orderId","accountId","instrumentId","orderType","quantity","orderStatus"]) if(a[key]!==b[key]) throw Error("Restart readback mismatch: "+key);' "$scratch/order.json" "$scratch/readback.json"
echo "SELL order persisted and remained readable after buy-sell-service restart."
