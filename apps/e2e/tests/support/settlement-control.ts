import { APIRequestContext, APIResponse, expect } from '@playwright/test';
import { OrderDto } from './orders';

// trading-service serves its test control and its settlement endpoint itself, never through a
// web app (C6), so they need their own address when the stack isn't on this machine.
const HOLDINGS_SERVICE_URL = process.env.E2E_HOLDINGS_URL ?? 'http://localhost:8084';

const faultsOf = (accountId: number) =>
  `${HOLDINGS_SERVICE_URL}/internal/holdings/control/settlement-faults/${accountId}`;

/**
 * Makes every settlement for one account fail part-way: after its writes have reached the
 * database and before they are committed. Only that account is affected, so a test breaks no
 * orders but its own. Fails the test if the control is not enabled, because a test that depends
 * on the failure cannot continue without it.
 */
export async function failSettlementsFor(request: APIRequestContext, accountId: number): Promise<void> {
  const response = await request.put(faultsOf(accountId));
  expect(response.status(),
    `fail settlements: is lmj.settlement.control-enabled on? ${await response.text()}`).toBe(200);
}

/** Lets the account's settlements work again. */
export async function restoreSettlementsFor(request: APIRequestContext, accountId: number): Promise<void> {
  const response = await request.delete(faultsOf(accountId));
  expect(response.status(), await response.text()).toBe(200);
}

/** How many settlements have been failed for the account since failSettlementsFor. */
export async function settlementFailuresOf(request: APIRequestContext, accountId: number): Promise<number> {
  const response = await request.get(faultsOf(accountId));
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).failures;
}

/**
 * Sends trading-service the settlement request of an order that has a fill price, exactly as
 * orders-service sends it (C6): the same fill, received again.
 */
export async function resendSettlement(request: APIRequestContext, accountId: number, instrumentId: number,
    order: OrderDto): Promise<APIResponse> {
  return request.post(`${HOLDINGS_SERVICE_URL}/internal/holdings/settle`, {
    data: {
      orderId: order.orderId, accountId, instrumentId,
      orderType: order.orderType, quantity: order.quantity, pricePerUnit: order.pricePerUnit,
    },
  });
}
