/**
 * Trade report response models.
 */

/**
 * A single period's aggregated trade data.
 */
export interface PeriodAggregation {
  /** Period key: YYYY-MM-DD (day), YYYY-Www (week), YYYY-MM (month), or YYYY (year) */
  period: string;
  /** Total number of filled orders (trades) in this period */
  tradeCount: number;
  /** Number of BUY orders in this period */
  buyCount: number;
  /** Number of SELL orders in this period */
  sellCount: number;
  /** Sum of gross amounts (quantity × price) for all trades in this period */
  totalValue: number;
}

/**
 * Trade aggregation report response.
 */
export interface TradeReportResponse {
  /** Array of aggregated periods (sorted by period, newest first) */
  data: PeriodAggregation[];
  /** Timestamp when the report was generated (ISO-8601 UTC) */
  generatedAt: string;
}
