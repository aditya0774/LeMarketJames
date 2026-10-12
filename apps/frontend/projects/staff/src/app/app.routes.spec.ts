import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Auth, Role } from './core/auth/auth';
import { routes } from './app.routes';

describe('Staff routes', () => {
  /** Opens an address as an account with these roles (none: signed out); returns where it ends up. */
  const open = async (url: string, ...roles: Role[]) => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    });
    if (roles.length) {
      const auth = TestBed.inject(Auth);
      auth.currentUser.set('someone');
      auth.roles.set(roles);
    }
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return { url: TestBed.inject(Router).url, page: harness.routeNativeElement as HTMLElement };
  };

  it('starts on the staff login', async () => {
    const { url, page } = await open('/');

    expect(url).toBe('/login');
    expect(page.querySelector('h2')?.textContent).toContain('Staff Login');
  });

  for (const address of ['/trade-search', '/analyst', '/analyst/reports/activity-by-stock', '/access-denied']) {
    it(`sends a signed-out visitor from ${address} to the staff login`, async () => {
      expect((await open(address)).url).toBe('/login');
    });
  }

  it('starts each signed-in role on its own dashboard', async () => {
    // Trading Ops has no dashboard of its own: trade search is where it starts.
    expect((await open('/', 'TRADING_OPS')).url).toBe('/trade-search');
    TestBed.resetTestingModule();
    expect((await open('/', 'ANALYST')).url).toBe('/analyst');
  });

  it('opens the Trading Ops pages for Trading Ops', async () => {
    const search = await open('/trade-search', 'TRADING_OPS');
    expect(search.url).toBe('/trade-search');
    expect(search.page.querySelector('h1')?.textContent).toContain('Trade search');
  });

  it('opens the Analyst pages for an Analyst', async () => {
    const dashboard = await open('/analyst', 'ANALYST');
    expect(dashboard.url).toBe('/analyst');
    expect(dashboard.page.textContent).toContain('Analyst Dashboard');

    TestBed.resetTestingModule();
    expect((await open('/analyst/reports/activity-by-stock', 'ANALYST')).url)
      .toBe('/analyst/reports/activity-by-stock');
  });

  it('refuses Trading Ops on the Analyst dashboard and on a report', async () => {
    const refused = await open('/analyst', 'TRADING_OPS');
    expect(refused.url).toBe('/access-denied');
    expect(refused.page.textContent).toContain('Access denied');
    expect(refused.page.textContent).not.toContain('Analyst Dashboard');

    TestBed.resetTestingModule();
    expect((await open('/analyst/reports/activity-by-stock', 'TRADING_OPS')).url).toBe('/access-denied');
  });

  it('refuses an Analyst on /trade-search', async () => {
    const refused = await open('/trade-search', 'ANALYST');
    expect(refused.url).toBe('/access-denied');
    expect(refused.page.textContent).not.toContain('Trade search');
  });

  // /trading-ops is no longer a route: it falls through to the start page, like any unknown address.
  it('sends an Analyst from the retired /trading-ops address to the Analyst dashboard', async () => {
    expect((await open('/trading-ops', 'ANALYST')).url).toBe('/analyst');
  });

  it('sends an unknown address to the start page', async () => {
    expect((await open('/no-such-page', 'ANALYST')).url).toBe('/analyst');
  });
});
