import { OrderResponse, orderTimestamp } from '../../../core/orders/order.service';

export interface InstrumentLookup {
  symbol: string;
  name: string;
}

export interface OrderHistoryRow {
  readonly order: OrderResponse;
  readonly symbol: string;
  readonly instrumentName: string;
  readonly submittedAt: string;
  readonly filledAt: string | null;
  readonly rejectionReason: string | null;
}

export function toOrderHistoryRow(
  order: OrderResponse,
  instrument?: InstrumentLookup,
): OrderHistoryRow {
  const symbol = order.symbol?.trim();

  return {
    order,
    symbol: symbol || instrument?.symbol || `#${order.instrumentId}`,
    instrumentName: instrument?.name || '',
    submittedAt: orderTimestamp(order.submittedAt),
    filledAt: order.filledAt ? orderTimestamp(order.filledAt) : null,
    rejectionReason: order.rejectionReason ?? null,
  };
}
