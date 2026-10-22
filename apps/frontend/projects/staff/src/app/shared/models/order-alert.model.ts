/**
 * Why an order was brought to Trading Operations' attention: a mirror of staff-ops-service's
 * AlertReason (C6, GET /api/v1/surveillance/alerts), with the name staff read for each.
 * A reason added there is added here.
 */
export const ALERT_REASONS = {
  LARGE_ORDER: 'Large order',
  SETTLEMENT_REFUSED: 'Settlement refused',
} as const;

export type AlertReason = keyof typeof ALERT_REASONS;

/** One alert (C6). What was ordered is null on a SETTLEMENT_REFUSED alert: its event does not carry it. */
export interface OrderAlert {
  alertId: number;
  orderId: number;
  accountId: number;
  instrumentId: number | null;
  side: 'BUY' | 'SELL' | null;
  quantity: number | null;
  /** Null for a SELL, which has no price until it fills. */
  price: number | null;
  reason: AlertReason;
  submittedAt: string | null;
  rejectionReason: string | null;
  /** ISO-8601 UTC; the list is newest first by it. */
  occurredAt: string;
}

export interface AlertList {
  success: boolean;
  /** Shares at which an order counts as large in this environment (C5). */
  largeOrderQuantity: number;
  alerts: OrderAlert[];
}

/** What the list is narrowed to; a part left out is not filtered on. Days are YYYY-MM-DD, whole UTC days. */
export interface AlertQuery {
  reason?: AlertReason;
  from?: string;
  to?: string;
}
