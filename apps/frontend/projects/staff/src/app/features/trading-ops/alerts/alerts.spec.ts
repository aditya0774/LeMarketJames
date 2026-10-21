import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AlertList, OrderAlert } from '../../../shared/models/order-alert.model';
import { Alerts } from './alerts';

const ALERTS = '/api/v1/surveillance/alerts';

/** The two kinds of alert in the C6 shape: a large BUY, and a refused settlement that carries no order details. */
const LARGE: OrderAlert = {
  alertId: 3, orderId: 42, accountId: 7, instrumentId: 5, side: 'BUY', quantity: 150, price: 244.2366,
  reason: 'LARGE_ORDER', submittedAt: '2026-10-08T14:30:00Z', rejectionReason: null, occurredAt: '2026-10-08T14:30:00Z',
};
const REFUSED: OrderAlert = {
  alertId: 4, orderId: 43, accountId: 8, instrumentId: null, side: null, quantity: null, price: null,
  reason: 'SETTLEMENT_REFUSED', submittedAt: null, rejectionReason: 'INSUFFICIENT_CASH', occurredAt: '2026-10-08T14:35:02.662391Z',
};

function list(alerts: OrderAlert[] = [REFUSED, LARGE]): AlertList {
  return { success: true, largeOrderQuantity: 100, alerts };
}

describe('Alerts', () => {
  let fixture: ComponentFixture<Alerts>;
  let httpMock: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const text = (node: Element | null) => node!.textContent!.replace(/\s+/g, ' ').trim();
  const alertsRequest = (): TestRequest => httpMock.expectOne((r) => r.url === ALERTS);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Alerts],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Alerts);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  async function respond(request: TestRequest, body: AlertList = list()) {
    request.flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function submit(values: { reason?: string; from?: string; to?: string }) {
    const component = fixture.componentInstance;
    component.reason = (values.reason ?? '') as typeof component.reason;
    component.from = values.from ?? '';
    component.to = values.to ?? '';
    (el().querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('loads every alert with no filter, and shows the tab bar to trade search', async () => {
    const request = alertsRequest();
    expect(request.request.params.keys()).toEqual([]);
    expect(el().textContent).toContain('Loading alerts');
    await respond(request);

    const tabs = Array.from(el().querySelectorAll('staff-trading-ops-nav a')).map((a) => [text(a), a.getAttribute('href')]);
    expect(tabs).toEqual([['Trade search', '/trade-search'], ['Alerts', '/alerts']]);
    expect(text(el().querySelector('.al-title p'))).toContain('large orders (100 shares or more)');
  });

  it('shows one row per alert, newest first, with what each kind carries', async () => {
    await respond(alertsRequest());

    const rows = Array.from(el().querySelectorAll('tbody tr'));
    expect(rows.map((r) => Array.from(r.querySelectorAll('td')).map(text))).toEqual([
      ['2026-10-08 14:35:02 UTC', 'Settlement refused', '43', '8', '—', '—', '—', '—', 'INSUFFICIENT_CASH'],
      ['2026-10-08 14:30:00 UTC', 'Large order', '42', '7', '5', 'BUY', '150', '244.2366', '—'],
    ]);
    expect(rows[0].querySelector('.reason')!.classList).toContain('refused');
    expect(text(el().querySelector('.al-status'))).toContain('2 alert(s)');
  });

  it('sends the reason and the UTC days chosen', async () => {
    await respond(alertsRequest());

    await submit({ reason: 'LARGE_ORDER', from: '2026-10-01', to: '2026-10-08' });

    const request = alertsRequest();
    expect(request.request.params.get('reason')).toBe('LARGE_ORDER');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-08');
    await respond(request, list([LARGE]));
    expect(el().querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('offers every reason the server names, by its staff name', () => {
    const options = Array.from(el().querySelectorAll('#al-reason option')).map((o) => [o.getAttribute('value'), text(o)]);

    expect(options).toEqual([['', 'All reasons'], ['LARGE_ORDER', 'Large order'], ['SETTLEMENT_REFUSED', 'Settlement refused']]);
    alertsRequest().flush(list());
  });

  it('refuses an end date before the start date without asking the server', async () => {
    await respond(alertsRequest());

    await submit({ from: '2026-10-08', to: '2026-10-01' });

    httpMock.expectNone((r) => r.url === ALERTS);
    expect(text(el().querySelector('[role="alert"]'))).toBe('End date must be on or after start date.');
  });

  it('Clear drops every filter and loads again', async () => {
    await respond(alertsRequest());
    await submit({ reason: 'SETTLEMENT_REFUSED' });
    await respond(alertsRequest(), list([REFUSED]));

    (el().querySelector('button.clear') as HTMLButtonElement).click();
    fixture.detectChanges();

    const request = alertsRequest();
    expect(request.request.params.keys()).toEqual([]);
    request.flush(list());
  });

  it('says so when nothing matches', async () => {
    await respond(alertsRequest(), list([]));

    expect(el().querySelector('table')).toBeNull();
    expect(text(el().querySelector('.al-status'))).toBe('No alerts match these filters.');
  });

  it.each([
    [403, 'Access denied. Alerts require Trading Operations.'],
    [401, 'Your session has expired. Please sign in again.'],
    [400, 'Check the filters and try again.'],
    [500, 'Unable to load alerts. Please try again.'],
  ])('explains a %i', async (status, message) => {
    alertsRequest().flush({ message: 'no' }, { status, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(el().querySelector('[role="alert"]'))).toBe(message);
    expect(el().querySelector('table')).toBeNull();
  });

  it("links each order to its timeline from the audit trail", async () => {
    await respond(alertsRequest());
    expect(text(el().querySelector('staff-order-timeline-panel'))).toContain('Select an order ID in the list');

    (el().querySelector('button[aria-label="Show the timeline of order 42"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/orders/42/timeline').flush([
      { eventType: 'SUBMITTED', occurredAt: '2026-10-08T14:30:00Z', details: { quantity: 150 } },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el().querySelector('tbody tr.selected')!.getAttribute('data-reason')).toBe('LARGE_ORDER');
    expect(text(el().querySelector('staff-order-timeline-panel h2'))).toBe('Order timeline #42');
    expect(el().querySelectorAll('staff-trade-timeline li')).toHaveLength(1);
  });
});
