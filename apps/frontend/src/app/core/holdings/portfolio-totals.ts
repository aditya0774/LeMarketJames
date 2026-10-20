import { HoldingDto } from '../../shared/models/holdings.model';
import { Quote } from '../../shared/models/quote.model';

export interface PortfolioTotals {
  value: number;
  cost: number;
  gainLoss: number;
  /** Gain/loss relative to cost basis; 0 when nothing is held. */
  gainLossPercent: number;
}

/**
 * Each holding re-priced at its latest live quote, so the Overview's money moves with the market
 * between holdings reloads (which happen only when an order or deposit changes the account). A stock
 * with no quote yet, or whose feed is down, keeps the value the backend computed at the last load.
 */
export function revalue(holdings: readonly HoldingDto[], quotes: Readonly<Record<string, Quote | null>>): HoldingDto[] {
  return holdings.map((h) => {
    const price = quotes[h.symbol]?.price;
    if (price == null) {
      return h;
    }
    const currentValue = h.quantity * price;
    const gainLoss = currentValue - h.totalCost;
    return {
      ...h,
      currentPrice: price,
      currentValue,
      gainLoss,
      gainLossPercent: h.totalCost ? (gainLoss / h.totalCost) * 100 : 0,
    };
  });
}

/** Sums the per-holding figures the backend already computes, for the Overview's headline cards. */
export function portfolioTotals(holdings: readonly HoldingDto[]): PortfolioTotals {
  const value = holdings.reduce((sum, h) => sum + h.currentValue, 0);
  const cost = holdings.reduce((sum, h) => sum + h.totalCost, 0);
  const gainLoss = value - cost;
  return { value, cost, gainLoss, gainLossPercent: cost ? (gainLoss / cost) * 100 : 0 };
}

/** One stock's slice of the portfolio, for the "What you own" donut. */
export interface AllocationSlice {
  symbol: string;
  /** Share of the portfolio's current value, 0–100. */
  percent: number;
  gainLossPercent: number;
}

/** Largest holding first; each slice is its share of today's total value. */
export function allocation(holdings: readonly HoldingDto[]): AllocationSlice[] {
  const total = holdings.reduce((sum, h) => sum + h.currentValue, 0);
  if (!total) {
    return [];
  }
  return [...holdings]
    .sort((a, b) => b.currentValue - a.currentValue)
    .map((h) => ({ symbol: h.symbol, percent: (h.currentValue / total) * 100, gainLossPercent: h.gainLossPercent }));
}

/**
 * Chart colours for the allocation, in order (largest holding first). A sixth or later holding
 * reuses them from the start. Shared so the Overview donut and the Holdings bar agree.
 */
export const ALLOCATION_COLORS: readonly string[] = ['var(--d1)', 'var(--d2)', 'var(--d3)', 'var(--d4)', 'var(--d5)'];

export function allocationColor(index: number): string {
  return ALLOCATION_COLORS[index % ALLOCATION_COLORS.length];
}
