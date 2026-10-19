-- 022: Daily price history, seeded with six months for every simulated stock
--
-- price_history holds one OHLC bar per stock per trading day. It backs the price charts
-- (core-service GET /api/quotes/{symbol}/history) and the portfolio-value chart (holdings-service
-- GET /api/v1/portfolio/history), contract C6. market-service keeps it current: every time it saves
-- the latest prices it also writes today's bar, so the history grows by one day per trading day.
--
-- The seed below fills the six months before the day it runs, so the charts have something to show
-- from the first start. Each stock's line is built to be believable and consistent with the rest of
-- the data:
--   * Shape: a random walk using the stock's own drift and volatility (instrument_market_params),
--     one step per weekday. The "random" numbers come from hashing (instrument, date), so every
--     database seeded on the same day gets exactly the same history.
--   * Anchors: the line is bent to pass exactly through the price of every FILLED order in the
--     window (the seeded fills of 011, and any others already in the database) and to end at the
--     stock's current price (its stored quote, else its initial price), so it joins the live price
--     without a jump and every trade sits on its chart. Between anchors the bend is spread evenly in
--     log-price, which keeps the walk's day-to-day texture.
-- Dates are relative to when it runs, like the rest of the seed data (C3). It is skipped once the
-- table has any rows, so re-running it never duplicates or rewrites history.

BEGIN;

CREATE TABLE IF NOT EXISTS price_history (
    instrument_id  INTEGER       NOT NULL REFERENCES instruments(instrument_id),
    trading_date   DATE          NOT NULL,  -- exchange-local trading day (America/New_York)
    open_price     NUMERIC(14,4) NOT NULL,
    high_price     NUMERIC(14,4) NOT NULL,
    low_price      NUMERIC(14,4) NOT NULL,
    close_price    NUMERIC(14,4) NOT NULL,
    volume         BIGINT        NOT NULL,
    PRIMARY KEY (instrument_id, trading_date)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM price_history) THEN
        RAISE NOTICE '022: price_history already has rows; seed skipped.';
        RETURN;
    END IF;

    INSERT INTO price_history (instrument_id, trading_date, open_price, high_price, low_price, close_price, volume)
    WITH
    today AS (
        SELECT (NOW() AT TIME ZONE 'America/New_York')::date AS d
    ),
    -- Weekdays from six months ago up to and including today (holidays are not modelled).
    days AS (
        SELECT g::date AS trading_date
        FROM today, generate_series(today.d - INTERVAL '6 months', today.d, INTERVAL '1 day') AS g
        WHERE EXTRACT(ISODOW FROM g) < 6
    ),
    params AS (
        SELECT p.instrument_id,
               p.drift::float8      AS mu,
               p.volatility::float8 AS sigma,
               p.avg_daily_volume,
               COALESCE(q.last_price, p.initial_price)::float8 AS end_price
        FROM instrument_market_params p
        LEFT JOIN market_quotes q ON q.instrument_id = p.instrument_id
    ),
    -- Four deterministic uniforms in (0, 1) per stock and day, from hashes of (instrument, date, k).
    uniforms AS (
        SELECT p.*, d.trading_date,
               ROW_NUMBER() OVER (PARTITION BY p.instrument_id ORDER BY d.trading_date) AS n,
               (hashtext(p.instrument_id || ':' || d.trading_date || ':1')::float8 + 2147483648.5) / 4294967297.0 AS u1,
               (hashtext(p.instrument_id || ':' || d.trading_date || ':2')::float8 + 2147483648.5) / 4294967297.0 AS u2,
               (hashtext(p.instrument_id || ':' || d.trading_date || ':3')::float8 + 2147483648.5) / 4294967297.0 AS u3,
               (hashtext(p.instrument_id || ':' || d.trading_date || ':4')::float8 + 2147483648.5) / 4294967297.0 AS u4
        FROM params p CROSS JOIN days d
    ),
    -- Box-Muller turns the uniforms into standard normal shocks: z for the price, the rest for
    -- the intraday range and the volume.
    shocks AS (
        SELECT *,
               sqrt(-2 * ln(u1)) * cos(2 * pi() * u2) AS z,
               sqrt(-2 * ln(u1)) * sin(2 * pi() * u2) AS z_range,
               sqrt(-2 * ln(u3)) * cos(2 * pi() * u4) AS z_volume
        FROM uniforms
    ),
    -- The free walk in log-price: GBM's daily log return, summed. One trading year is 252 days.
    walk AS (
        SELECT *,
               SUM((mu - sigma * sigma / 2) / 252 + sigma * sqrt(1.0 / 252) * z)
                   OVER (PARTITION BY instrument_id ORDER BY n) AS w,
               MAX(n) OVER (PARTITION BY instrument_id) AS last_n
        FROM shocks
    ),
    -- Fills in the window, moved to the trading day they belong to (a weekend fill counts on the
    -- Friday before) and averaged when a stock filled more than once that day.
    fill_anchors AS (
        SELECT w.instrument_id, w.n, AVG(ln(o.price_per_unit::float8)) AS log_price
        FROM orders o
        JOIN LATERAL (
            SELECT MAX(d.trading_date) AS trading_date
            FROM days d
            WHERE d.trading_date <= o.filled_at::date
        ) day ON day.trading_date IS NOT NULL
        JOIN walk w ON w.instrument_id = o.instrument_id AND w.trading_date = day.trading_date
        WHERE o.order_status = 'FILLED'
          AND o.price_per_unit > 0
          AND o.filled_at IS NOT NULL
          -- The last day is pinned to the current price below, which takes priority.
          AND w.n < w.last_n
        GROUP BY w.instrument_id, w.n
    ),
    -- What each anchor needs added to the free walk to land on its price.
    anchors AS (
        SELECT a.instrument_id, a.n, a.log_price - w.w AS shift
        FROM fill_anchors a
        JOIN walk w ON w.instrument_id = a.instrument_id AND w.n = a.n
        UNION ALL
        SELECT instrument_id, n, ln(end_price) - w
        FROM walk
        WHERE n = last_n
    ),
    -- The shift for every day: straight-line between the anchors either side; before the first
    -- anchor, that anchor's shift.
    shifted AS (
        SELECT w.*,
               CASE
                   WHEN prev.n IS NULL THEN nxt.shift
                   WHEN nxt.n = prev.n THEN prev.shift
                   ELSE prev.shift + (nxt.shift - prev.shift) * (w.n - prev.n)::float8 / (nxt.n - prev.n)
               END AS shift
        FROM walk w
        LEFT JOIN LATERAL (
            SELECT a.n, a.shift FROM anchors a
            WHERE a.instrument_id = w.instrument_id AND a.n <= w.n
            ORDER BY a.n DESC LIMIT 1
        ) prev ON TRUE
        LEFT JOIN LATERAL (
            SELECT a.n, a.shift FROM anchors a
            WHERE a.instrument_id = w.instrument_id AND a.n >= w.n
            ORDER BY a.n LIMIT 1
        ) nxt ON TRUE
    ),
    closes AS (
        SELECT *, exp(w + shift) AS close
        FROM shifted
    ),
    bars AS (
        SELECT *,
               -- The day opens where the day before closed; the first day opens just off its close.
               COALESCE(LAG(close) OVER (PARTITION BY instrument_id ORDER BY n),
                        close * exp(-sigma * sqrt(1.0 / 252) * z)) AS open,
               -- How far the day ranged beyond its open and close.
               abs(z_range) * sigma * sqrt(1.0 / 252) / 2 AS spread
        FROM closes
    )
    SELECT instrument_id,
           trading_date,
           ROUND(open::numeric, 4),
           ROUND((GREATEST(open, close) * (1 + spread))::numeric, 4),
           ROUND((LEAST(open, close) * (1 - spread))::numeric, 4),
           ROUND(close::numeric, 4),
           GREATEST(0, ROUND(avg_daily_volume * exp(0.3 * z_volume - 0.045)))::bigint
    FROM bars
    -- Today's bar comes from the live market (market-service writes it), not from the seed.
    WHERE trading_date < (SELECT d FROM today);

    RAISE NOTICE '022: seeded % price_history rows.', (SELECT COUNT(*) FROM price_history);
END
$$;

COMMIT;
