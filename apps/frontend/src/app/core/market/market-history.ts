import { Injectable, computed, inject, signal } from '@angular/core';
import { Quote } from '../../shared/models/quote.model';
import { PriceHistory } from '../history/price-history';

/** Points kept per stock: a day of minutes, which also covers markets open around the clock. */
export const MARKET_HISTORY_LENGTH = 1440;

/** One stock's trace: a price per minute, oldest first, and which minute the last point is for. */
export interface Trace {
  points: number[];
  /** Epoch minutes of the last point, or null when unknown (it is then replaced by a new point). */
  minute: number | null;
}

const MINUTE_MS = 60_000;

function minuteOf(time: number): number | null {
  return Number.isFinite(time) ? Math.floor(time / MINUTE_MS) : null;
}

/**
 * Folds a new batch of quotes into each stock's trace, one point per minute: while a quote is still
 * from the same minute as the last point it replaces it (so the graph's end follows the live price),
 * and a quote from a later minute adds a point. After the close the quote's time stops changing, so
 * the trace stops growing. A stock seen for the first time is seeded with [openPrice, price] so its
 * graph shows today's direction straight away.
 */
export function appendQuotes(
  history: Readonly<Record<string, Trace>>,
  quotes: Readonly<Record<string, Quote | null>>,
  maxLength = MARKET_HISTORY_LENGTH,
): Record<string, Trace> {
  const next: Record<string, Trace> = { ...history };
  for (const [symbol, quote] of Object.entries(quotes)) {
    if (!quote) {
      continue;
    }
    const minute = minuteOf(Date.parse(quote.lastUpdate));
    const previous = next[symbol];
    if (!previous?.points.length) {
      next[symbol] = { points: [quote.openPrice, quote.price], minute };
    } else if (minute !== null && minute === previous.minute) {
      next[symbol] = { points: [...previous.points.slice(0, -1), quote.price], minute };
    } else {
      next[symbol] = { points: [...previous.points, quote.price].slice(-maxLength), minute };
    }
  }
  return next;
}

/**
 * Replaces traces with the server's record of today (`GET /api/quotes/intraday`), which ends with
 * the live price, so it is taken as this minute's point. A stock with fewer than two points keeps
 * what it has, since a single point draws no line.
 */
export function seedTraces(
  history: Readonly<Record<string, Trace>>,
  today: Readonly<Record<string, readonly number[]>>,
  now: number,
): Record<string, Trace> {
  const next: Record<string, Trace> = { ...history };
  for (const [symbol, points] of Object.entries(today)) {
    if (points.length >= 2) {
      next[symbol] = { points: points.slice(-MARKET_HISTORY_LENGTH), minute: minuteOf(now) };
    }
  }
  return next;
}

/**
 * The trend graphs' price traces, kept for the whole session rather than by the page that draws
 * them: the Overview and Market tabs both record each poll here and read it back, so leaving a tab
 * and returning picks the graphs up where they were instead of restarting them.
 *
 * `load()` fills them once with everything since today's open (trading-service fills in any part
 * of the session it missed on startup), so the graphs show the whole day from the first visit.
 * While neither tab is open nothing polls; the next poll after a gap adds a point and carries on.
 */
@Injectable({ providedIn: 'root' })
export class MarketHistory {
  private readonly priceHistory = inject(PriceHistory);
  private readonly state = signal<Record<string, Trace>>({});
  /** Set while today's history is loaded or loading; cleared on failure so a later visit retries. */
  private requested = false;

  /** Each stock's prices today, oldest first. */
  readonly traces = computed(() =>
    Object.fromEntries(Object.entries(this.state()).map(([symbol, trace]) => [symbol, trace.points])),
  );

  /** Fetches today's history once per session; until it arrives, or if it fails, polls seed the graphs. */
  load(): void {
    if (this.requested) {
      return;
    }
    this.requested = true;
    this.priceHistory.intraday().subscribe({
      next: (today) => this.state.update((history) => seedTraces(history, today, Date.now())),
      error: () => (this.requested = false),
    });
  }

  record(quotes: Readonly<Record<string, Quote | null>>): void {
    this.state.update((history) => appendQuotes(history, quotes));
  }
}
