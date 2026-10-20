import { ChartPoint, ChartRange } from '../../../shared/models/chart.model';

/** What a purchase made on a past day would be worth now. All money is in dollars. */
export interface Simulation {
  /** The trading day the purchase happens on, as YYYY-MM-DD (may be later than the day asked for). */
  buyDate: string;
  buyPrice: number;
  /** Fractional: the dollar amount divided by the price, so any amount buys something. */
  shares: number;
  /** The latest price, i.e. the last point of the history. */
  currentPrice: number;
  currentValue: number;
  gain: number;
  gainPercent: number;
  /** Calendar days from the purchase to the latest price. */
  daysHeld: number;
  /** What the holding was worth on each trading day from the purchase on, for the chart. */
  series: ChartPoint[];
}

const DAY_MS = 86_400_000;

/**
 * The calendar day of a history point, as YYYY-MM-DD. Daily bars are stamped at the exchange's
 * closing bell, which in UTC is still the trading day, so the UTC date is that day.
 */
export function dayOf(time: number): string {
  return new Date(time).toISOString().slice(0, 10);
}

/**
 * What putting `amount` dollars into a stock on `buyDay` would be worth at the latest price.
 *
 * `points` is the stock's daily history, oldest first, ending with the live price. The purchase is
 * made on the first trading day on or after `buyDay` (weekends have no price), a day before the
 * history starts buys at its first price, and a day after it ends buys at the latest. Returns null
 * when there is nothing to work with: no prices, or an amount that is not a positive number.
 */
export function simulate(points: readonly ChartPoint[], buyDay: string, amount: number): Simulation | null {
  if (!points.length || !Number.isFinite(amount) || amount <= 0) {
    return null;
  }
  const found = points.findIndex((p) => dayOf(p.time) >= buyDay);
  const start = found === -1 ? points.length - 1 : found;
  const bought = points[start];
  const latest = points[points.length - 1];
  if (!(bought.value > 0)) {
    return null;
  }

  const shares = amount / bought.value;
  const currentValue = shares * latest.value;
  const gain = currentValue - amount;
  return {
    buyDate: dayOf(bought.time),
    buyPrice: bought.value,
    shares,
    currentPrice: latest.value,
    currentValue,
    gain,
    gainPercent: (gain / amount) * 100,
    daysHeld: Math.round((Date.parse(dayOf(latest.time)) - Date.parse(dayOf(bought.time))) / DAY_MS),
    series: points.slice(start).map((p) => ({ time: p.time, value: shares * p.value })),
  };
}

/** The smallest chart range that covers a holding of `daysHeld` days, so the chart's axis suits it. */
export function chartRangeFor(daysHeld: number): ChartRange {
  if (daysHeld <= 7) {
    return '1W';
  }
  if (daysHeld <= 31) {
    return '1M';
  }
  return daysHeld <= 92 ? '3M' : '6M';
}

/**
 * The day `months` calendar months before `day` (YYYY-MM-DD). When that month is shorter the last
 * day of it is used, so 31 March minus one month is 28 (or 29) February, never a day in March.
 */
export function monthsBefore(day: string, months: number): string {
  const [year, month, date] = day.split('-').map(Number);
  const target = new Date(Date.UTC(year, month - 1 - months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(date, lastDay));
  return target.toISOString().slice(0, 10);
}
