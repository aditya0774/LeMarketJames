import { Page } from '@playwright/test';

/** The signed-in sections, as named in the header's tab bar. */
export type Tab = 'Overview' | 'Market' | 'Holdings' | 'Orders';

/** Opens a section the way a user does, through the tab bar in the header. */
export async function openTab(page: Page, tab: Tab): Promise<void> {
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: tab, exact: true }).click();
}
