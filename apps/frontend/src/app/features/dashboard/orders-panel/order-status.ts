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

/** Maps a status onto the mockup's pill colours: green filled, red rejected, gold in flight. */
export function statusPillClass(status: OrderStatus): string {
  if (status === 'FILLED') return 'pill-filled';
  if (status === 'REJECTED') return 'pill-rejected';
  return 'pill-pending';
}

/** "SUBMITTED" → "Submitted". */
export function statusLabel(status: OrderStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}
