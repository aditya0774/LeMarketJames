const REJECTION_REASON_COPY: Record<string, string> = {
  INSUFFICIENT_CASH: 'Order rejected because your available cash is too low for this buy order.',
  INSUFFICIENT_HOLDINGS: 'Order rejected because you do not hold enough shares to sell that quantity.',
  NOT_TRADABLE: 'Order rejected because this stock is currently not tradable.',
  PRICE_UNAVAILABLE: 'Order rejected because a live market price was unavailable at execution time.',
  STALE_QUOTE: 'Order rejected because the market quote was too old to safely execute the trade.',
  MARKET_CLOSED: 'Order rejected because the market is currently closed for this order type.',
  ACCOUNT_RESTRICTED: 'Order rejected because this account is restricted from trading.',
  LOCATION_RESTRICTED: 'Order rejected because trading is restricted for this account location.',
  REJECTED_BY_OPERATIONS: 'Order rejected by operations review.',
};

export function rejectionReasonLabel(reason: string | null | undefined): string | null {
  if (!reason) return null;
  return REJECTION_REASON_COPY[reason] ?? `Order rejected (${reason}).`;
}
