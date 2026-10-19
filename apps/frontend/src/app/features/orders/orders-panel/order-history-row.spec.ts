import { describe, expect, it } from 'vitest';
import { OrderResponse } from '../../../core/orders/order.service';
import { toOrderHistoryRow } from './order-history-row';

function baseOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return {
    success: true,
    orderId: 42,
    accountId: 7,
    instrumentId: 5,
    orderType: 'BUY',
    quantity: 10,
    pricePerUnit: 123.45,
    orderStatus: 'FILLED',
    rejectionReason: null,
    submittedAt: '2026-10-12T13:45:00',
    acceptedAt: '2026-10-12T13:45:01',
    filledAt: '2026-10-12T13:45:03',
    createdAt: '2026-10-12T13:45:00',
    updatedAt: '2026-10-12T13:45:03',
    ...overrides,
  };
}

describe('toOrderHistoryRow', () => {
  it('prefers backend symbol when available', () => {
    const order = baseOrder({ symbol: 'AAPL' });
    const row = toOrderHistoryRow(order, { symbol: 'MSFT', name: 'Microsoft Corp.' });

    expect(row.symbol).toBe('AAPL');
  });

  it('falls back to instrument lookup symbol when payload has no symbol', () => {
    const order = baseOrder({ symbol: null });
    const row = toOrderHistoryRow(order, { symbol: 'MSFT', name: 'Microsoft Corp.' });

    expect(row.symbol).toBe('MSFT');
    expect(row.instrumentName).toBe('Microsoft Corp.');
  });

  it('falls back to instrument id marker when symbol data is unavailable', () => {
    const order = baseOrder({ instrumentId: 19, symbol: null });
    const row = toOrderHistoryRow(order);

    expect(row.symbol).toBe('#19');
    expect(row.instrumentName).toBe('');
  });

  it('normalizes submitted and filled timestamps to explicit UTC when needed', () => {
    const order = baseOrder({ submittedAt: '2026-10-12T13:45:00', filledAt: '2026-10-12T13:45:03' });
    const row = toOrderHistoryRow(order);

    expect(row.submittedAt).toBe('2026-10-12T13:45:00Z');
    expect(row.filledAt).toBe('2026-10-12T13:45:03Z');
  });

  it('preserves explicit timezone offsets from backend payloads', () => {
    const order = baseOrder({
      submittedAt: '2026-10-12T13:45:00+05:30',
      filledAt: '2026-10-12T13:45:03+05:30',
    });
    const row = toOrderHistoryRow(order);

    expect(row.submittedAt).toBe('2026-10-12T13:45:00+05:30');
    expect(row.filledAt).toBe('2026-10-12T13:45:03+05:30');
  });

  it('maps rejection reason as null when backend omits it', () => {
    const order = baseOrder({
      orderStatus: 'REJECTED',
      rejectionReason: undefined,
      filledAt: null,
      pricePerUnit: null,
    });
    const row = toOrderHistoryRow(order);

    expect(row.rejectionReason).toBeNull();
    expect(row.filledAt).toBeNull();
  });
});
