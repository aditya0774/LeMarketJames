"""Populate the local LMKT-43 demo through normal order placement and settlement.

Apply database/schema/019_segment_report_demo_clients.sql first. Requires the trading
stack, available quotes and an open market. Re-running resumes each dedicated demo
account up to its target trade count; it never rewrites fills or audit records.
Run only one copy at a time. Uses Python's standard library.
"""
import argparse
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request


def request(opener, base, path, data=None):
    payload = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(base + path, data=payload,
                                 headers={"Content-Type": "application/json"})
    try:
        with opener.open(req, timeout=15) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{path}: HTTP {error.code}: {error.read().decode()}") from error


def wait_for_fill(opener, base, order_id):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        order = request(opener, base, f"/api/v1/orders/{order_id}")
        if order["orderStatus"] == "FILLED":
            return
        if order["orderStatus"] == "REJECTED":
            raise RuntimeError(f"Demo order {order_id} rejected: {order.get('rejectionReason')}")
        time.sleep(1)
    raise RuntimeError(f"Demo order {order_id} is still open; check market hours and execution before retrying.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8089")
    args = parser.parse_args()
    password = os.environ.get("DEMO_PASSWORD", "Pass123!")
    # Different activity and sizes make segment comparisons visible without changing prices.
    plans = [(n, 4, 10) for n in range(1, 4)]
    plans += [(n, 8, 40) for n in range(4, 6)]
    plans += [(n, 6, 150) for n in range(6, 8)]
    for number, target, quantity in plans:
        username = f"demo_segment_{number}"
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        login = request(opener, args.base_url, "/api/auth/login", {
            "username": username + "@seed.lemarket.com", "password": password})
        account_id = login["accountId"]
        instruments = request(opener, args.base_url, "/api/v1/instruments")
        ids = {row["symbol"]: row["instrumentId"] for row in instruments}
        orders = request(opener, args.base_url, f"/api/v1/orders/account/{account_id}")
        for order in orders:
            if order["orderStatus"] not in ("FILLED", "REJECTED"):
                wait_for_fill(opener, args.base_url, order["orderId"])
        orders = request(opener, args.base_url, f"/api/v1/orders/account/{account_id}")
        filled = sum(order["orderStatus"] == "FILLED" for order in orders)
        for index in range(filled, target):
            # Complete each buy/sell pair on the same instrument before moving on.
            symbol = ("AAPL", "MSFT", "NVDA")[(index // 2) % 3]
            side = "BUY" if index % 2 == 0 else "SELL"
            order = request(opener, args.base_url, "/api/v1/orders", {
                "accountId": account_id, "instrumentId": ids[symbol],
                "orderType": side, "quantity": quantity})
            wait_for_fill(opener, args.base_url, order["orderId"])
            print(f"{username}: {index + 1}/{target} filled ({side} {quantity} {symbol})", flush=True)
        print(f"{username}: demo ready ({max(filled, target)} filled trades)", flush=True)


if __name__ == "__main__":
    main()
