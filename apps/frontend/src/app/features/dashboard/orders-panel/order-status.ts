import { OrderResponse } from '../../../core/orders/order.service';

export type OrderStatus = OrderResponse['orderStatus'];

/** Dashboard filter chips. Grouped from the real DB statuses (see orders.order_status CHECK). */
export type OrderFilter = 'ALL' | 'OPEN' | 'FILLED' | 'REJECTED';

export const ORDER_FILTERS: readonly { value: OrderFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'OPEN', label: 'Open' },
  { value: 'FILLED', label: 'Filled' },
  { value: 'REJECTED', label: 'Rejected' },
];

const OPEN_STATUSES: readonly OrderStatus[] = ['SUBMITTED', 'ACCEPTED', 'PENDING', 'DELAYED'];

/** An order still waiting to execute, which is what the "Open orders" stat counts. */
export function isOpenStatus(status: OrderStatus): boolean {
  return OPEN_STATUSES.includes(status);
}

export function matchesFilter(status: OrderStatus, filter: OrderFilter): boolean {
  switch (filter) {
    case 'ALL':
      return true;
    case 'OPEN':
      return isOpenStatus(status);
    default:
      return status === filter;
  }
}

/**
 * One badge style per status (the glyph for each is in styles/base.css): green filled, red
 * rejected, gold for the two "on its way" states, and quieter outlines for just-submitted
 * and delayed (waiting for the market to open).
 */
export function statusPillClass(status: OrderStatus): string {
  return `pill-${status.toLowerCase()}`;
}

/** "SUBMITTED" → "Submitted". */
export function statusLabel(status: OrderStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}
