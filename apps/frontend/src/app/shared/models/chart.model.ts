/**
 * The spans a price chart can show, matching the backend's `range` values (C6: stock history and
 * portfolio history). 1D is drawn from today's minute prices, the rest from daily closes.
 */
export const CHART_RANGES = ['1D', '1W', '1M', '3M', '6M'] as const;
export type ChartRange = (typeof CHART_RANGES)[number];

/** How a range reads in a sentence: "▲ +$12.00 (+1.2%) past month". */
export const CHART_RANGE_LABELS: Record<ChartRange, string> = {
  '1D': 'today',
  '1W': 'past week',
  '1M': 'past month',
  '3M': 'past 3 months',
  '6M': 'past 6 months',
};

/** One point on a chart: epoch milliseconds and the value then. */
export interface ChartPoint {
  time: number;
  value: number;
}

/** GET /api/quotes/{symbol}/history */
export interface QuoteHistoryResponse {
  success: boolean;
  symbol: string;
  range: ChartRange;
  points: { time: string; price: number }[];
}

/** GET /api/v1/portfolio/history */
export interface PortfolioHistoryResponse {
  success: boolean;
  range: ChartRange;
  points: { time: string; value: number }[];
}

/** GET /api/quotes/intraday: every stock's prices over its current trading day, oldest first. */
export interface QuoteIntradayResponse {
  success: boolean;
  points: Record<string, number[]>;
}
