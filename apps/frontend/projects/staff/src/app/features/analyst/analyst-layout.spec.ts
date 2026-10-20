import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AnalystLayout } from './analyst-layout';

@Component({ template: '<p class="page">page</p>' })
class Page {}

describe('AnalystLayout', () => {
  async function open(url: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'analyst',
            component: AnalystLayout,
            children: [
              { path: '', component: Page },
              { path: 'reports/:report', component: Page },
            ],
          },
        ]),
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.fixture.nativeElement as HTMLElement;
  }

  const tabs = (page: HTMLElement) =>
    Array.from(page.querySelectorAll('nav[aria-label="Analyst reports"] a')).map((a) => a.textContent?.trim());

  it('lists Volume and every report as tabs', async () => {
    expect(tabs(await open('/analyst'))).toEqual(['Volume', 'By period', 'By stock', 'By segment', 'Overnight']);
  });

  it('heads the Volume page and marks its tab', async () => {
    const page = await open('/analyst');

    expect(page.querySelector('h1')?.textContent).toBe('Trading volume');
    expect(page.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('Volume');
    expect(page.querySelector('.page')).not.toBeNull();
  });

  it('heads a report with its own title and subtitle', async () => {
    const page = await open('/analyst/reports/activity-by-stock');

    expect(page.querySelector('h1')?.textContent).toBe('Activity by stock');
    expect(page.textContent).toContain('Trading across all clients, grouped by stock.');
    expect(page.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('By stock');
  });
});
