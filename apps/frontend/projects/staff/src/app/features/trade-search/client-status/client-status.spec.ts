import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ClientStatusPanel } from './client-status';
import { ClientStatus } from '../../../core/clients/client-status.service';

describe('Trading Ops account status panel (BR-03)', () => {
  let http: HttpTestingController;
  const url = '/api/v1/clients/7/status';
  const active: ClientStatus = { clientId: 7, fullName: 'Bronny James', status: 'ACTIVE', changes: [] };
  const suspended: ClientStatus = {
    clientId: 7, fullName: 'Bronny James', status: 'SUSPENDED',
    changes: [{ oldStatus: 'ACTIVE', newStatus: 'SUSPENDED', reason: 'Under review', changedBy: 'ops', changedAt: '2026-10-21T14:05:09.123Z' }],
  };

  function render(clientId = 7) {
    const fixture = TestBed.createComponent(ClientStatusPanel);
    fixture.componentRef.setInput('clientId', clientId);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const refresh = () => { fixture.componentRef.changeDetectorRef.markForCheck(); fixture.detectChanges(); };
    return { fixture, component: fixture.componentInstance, el, refresh };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ClientStatusPanel], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('shows the current status and an empty history', () => {
    const { el, refresh } = render();
    http.expectOne(r => r.method === 'GET' && r.url.endsWith(url)).flush(active);
    refresh();
    expect(el.querySelector('.cs-head .pill')?.textContent).toContain('Active');
    expect(el.textContent).toContain('Bronny James (Client ID 7)');
    expect(el.textContent).toContain('No status changes since the account was opened.');
    // The current status is not offered as a change.
    const options = Array.from(el.querySelectorAll('#cs-status option')).map(o => o.textContent?.trim());
    expect(options).toEqual(['Choose a status', 'Suspended', 'Closed', 'Expired']);
  });

  it('asks for a status and a reason before confirming, and sends nothing until confirmed', () => {
    const { component, el, refresh } = render();
    http.expectOne(r => r.url.endsWith(url)).flush(active);

    component.review();
    expect(component.error()).toBe('Choose the new status.');
    component.next = 'SUSPENDED'; component.reason = '   '; component.review();
    expect(component.error()).toBe('Give a reason for the change.');

    component.reason = ' Under review '; component.review(); refresh();
    expect(component.confirming()).toBe(true);
    expect(el.querySelector('[role="alertdialog"]')?.textContent).toContain("Change Bronny James's account to Suspended?");
    expect(el.querySelector('[role="alertdialog"]')?.textContent).toContain('signed out at once');
    http.expectNone(r => r.method === 'PUT');

    component.cancel(); refresh();
    expect(el.querySelector('[role="alertdialog"]')).toBeNull();
    http.expectNone(r => r.method === 'PUT');
  });

  it('changes the status on confirmation and shows the new history', () => {
    const { component, el, refresh } = render();
    http.expectOne(r => r.url.endsWith(url)).flush(active);
    component.next = 'SUSPENDED'; component.reason = ' Under review '; component.review();

    component.confirm();
    const put = http.expectOne(r => r.method === 'PUT' && r.url.endsWith(url));
    expect(put.request.body).toEqual({ status: 'SUSPENDED', reason: 'Under review' });
    put.flush(suspended); refresh();

    expect(component.confirming()).toBe(false);
    expect(el.textContent).toContain('The account is now suspended.');
    expect(el.querySelector('.cs-head .pill')?.textContent).toContain('Suspended');
    const row = el.querySelector('.cs-history tbody tr')?.textContent ?? '';
    expect(row).toContain('2026-10-21 14:05 UTC');
    expect(row).toContain('Active → Suspended');
    expect(row).toContain('ops');
    expect(row).toContain('Under review');
  });

  it("shows the server's field message when a change is refused", () => {
    const { component } = render();
    http.expectOne(r => r.url.endsWith(url)).flush(active);
    component.next = 'CLOSED'; component.reason = 'x'; component.review(); component.confirm();
    http.expectOne(r => r.method === 'PUT').flush(
      { errors: { status: 'The account is already CLOSED.' } }, { status: 400, statusText: 'Bad Request' });
    expect(component.error()).toBe('The account is already CLOSED.');
  });

  for (const [status, message] of [[404, 'No client has this ID.'], [403, 'Access denied'], [500, 'Unable to load']] as const) {
    it('explains a load failure with HTTP ' + status, () => {
      const { component } = render();
      http.expectOne(r => r.url.endsWith(url)).flush({}, { status, statusText: 'Error' });
      expect(component.error()).toContain(message);
      expect(component.client()).toBeNull();
    });
  }

  it('loads the new client when the id changes', () => {
    const { fixture } = render();
    http.expectOne(r => r.url.endsWith(url)).flush(active);
    fixture.componentRef.setInput('clientId', 8);
    fixture.detectChanges();
    http.expectOne(r => r.url.endsWith('/api/v1/clients/8/status')).flush({ ...active, clientId: 8 });
    expect(fixture.componentInstance.client()?.clientId).toBe(8);
  });
});
