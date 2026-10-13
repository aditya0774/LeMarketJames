import { APIRequestContext, expect } from '@playwright/test';

const EXECUTION_SERVICE_URL = process.env.E2E_EXECUTION_URL ?? 'http://localhost:8085';

export async function setExecutionControl(request: APIRequestContext, enabled: boolean,
    settlementAvailable: boolean): Promise<void> {
  const response = await request.put(`${EXECUTION_SERVICE_URL}/internal/execution/control`, {
    data: { enabled, settlementAvailable },
  });
  expect(response.status(), await response.text()).toBe(200);
}
