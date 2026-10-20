import { APIRequestContext, expect } from '@playwright/test';
import { TestUser } from './users';

/** The order DTO of contracts/C6-api.md, limited to the fields the execution tests read. */
export interface OrderDto {
  orderId: number;
  orderType: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit: number | null;
  orderStatus: string;
  rejectionReason: string | null;
  /** Order timestamps are UTC without an offset (C6); read them with utc(). */
  submittedAt: string;
  acceptedAt: string | null;
  filledAt: string | null;
  quoteSource: string | null;
  /** ISO-8601 UTC with an offset, unlike the order timestamps. */
  quoteTime: string | null;
}

/**
 * The feed name stored as the source of every execution quote. Mirrors QUOTE_SOURCE in
 * orders-service's MarketOrderExecutor, which defines it; change both together.
 */
export const QUOTE_SOURCE = 'SIMULATED_FEED';

/** Logs in through the API, leaving the jwt cookie on this request context; returns the account id. */
export async function loginViaApi(request: APIRequestContext, user: TestUser): Promise<number> {
  const response = await request.post('/api/auth/login', { data: { username: user.email, password: user.password } });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).accountId;
}

/** Looks the stock up in the supported list rather than assuming a database id. */
export async function instrumentIdOf(request: APIRequestContext, symbol: string): Promise<number> {
  const instruments = await (await request.get('/api/v1/instruments')).json();
  const match = instruments.find((instrument: { symbol: string }) => instrument.symbol === symbol);
  expect(match, `${symbol} is in the supported stock list`).toBeTruthy();
  return match.instrumentId;
}

/**
 * Places a market order through the trading app, i.e. through the real submission and validation
 * code. Placement only saves the order; the background worker accepts and executes it.
 */
export async function placeOrder(request: APIRequestContext, accountId: number, instrumentId: number,
    orderType: 'BUY' | 'SELL', quantity = 1): Promise<OrderDto> {
  // pricePerUnit is deliberately absurd: the server must ignore browser prices (C6).
  // Fresh-quote reads can race market ticks in shared CI stacks; retry transient stale-quote rejects.
  for (let attempt = 0; attempt < 5; attempt++) {
    const response = await request.post('/api/v1/orders', {
      data: { accountId, instrumentId, orderType, quantity, pricePerUnit: 0.01 },
    });
    if (response.status() === 201) {
      const order: OrderDto = await response.json();
      expect(order.orderStatus).toBe('SUBMITTED');
      return order;
    }

    const body = await response.text();
    let code: string | null = null;
    try {
      code = JSON.parse(body).code ?? null;
    } catch {
      code = null;
    }
    const staleQuote = response.status() === 400 && code === 'STALE_QUOTE';
    if (!staleQuote || attempt === 4) {
      expect(response.status(), body).toBe(201);
    }
    await new Promise(resolve => setTimeout(resolve, 250));
  }

  throw new Error('Failed to place order after stale-quote retries.');
}

export async function getOrder(request: APIRequestContext, orderId: number): Promise<OrderDto> {
  const response = await request.get(`/api/v1/orders/${orderId}`);
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

/** Waits for the worker to finish the order, and returns it as a later read sees it. */
export async function waitForFinalStatus(request: APIRequestContext, orderId: number): Promise<OrderDto> {
  await expect.poll(async () => {
    const status = (await getOrder(request, orderId)).orderStatus;
    // DELAYED means the worker is waiting for the exchange to open, which no timeout will outlast.
    if (status === 'DELAYED') {
      throw new Error(`Order ${orderId} is DELAYED: run the stack with SIM_RESPECT_MARKET_HOURS=false`);
    }
    return status;
  }, { timeout: 20000 }).toMatch(/^(FILLED|REJECTED)$/);
  return getOrder(request, orderId);
}

/** Reads an offset-free order timestamp as the UTC instant it represents, in milliseconds. */
export function utc(orderTimestamp: string): number {
  return Date.parse(`${orderTimestamp}Z`);
}
