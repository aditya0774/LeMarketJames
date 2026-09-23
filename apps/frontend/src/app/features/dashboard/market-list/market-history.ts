import { Quote } from '../../../shared/models/quote.model';

/** Points kept per stock: about a minute and a half at the dashboard refresh rate. */
export const MARKET_HISTORY_LENGTH = 40;

/**
 * Folds a new batch of quotes into each stock's price trace. The first quote seeds
 * [openPrice, price] so every graph shows today's direction immediately, instead of
 * staying empty until enough polls arrive. (No history endpoint exists yet.)
 */
export function appendQuotes(
  history: Readonly<Record<string, readonly number[]>>,
  quotes: Readonly<Record<string, Quote | null>>,
  maxLength = MARKET_HISTORY_LENGTH,
): Record<string, number[]> {
  const next: Record<string, number[]> = {};
  for (const [symbol, points] of Object.entries(history)) {
    next[symbol] = [...points];
  }
  for (const [symbol, quote] of Object.entries(quotes)) {
    if (!quote) continue;
    const previous = next[symbol];
    next[symbol] = previous?.length
      ? [...previous, quote.price].slice(-maxLength)
      : [quote.openPrice, quote.price];
  }
  return next;
}
