import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TradeSearch } from './trade-search';

describe('TradeSearch with mocked C6 responses', () => {
  let fixture: ComponentFixture<TradeSearch>;
  let component: TradeSearch;
  let http: HttpTestingController;
  const trade = { orderId: 42, clientId: 7, accountId: 9, instrumentId: 5,
    symbol: 'AAPL', side: 'BUY', quantity: 2, pricePerUnit: 123.4567, filledAt: '2026-01-10T12:00:00' };
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TradeSearch], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(TradeSearch);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });
  afterEach(() => http.verify());
  const pending = () => http.expectOne(r => r.url.endsWith('/api/v1/orders/trades/search'));
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('submits the order form and renders price precision and UTC time', async () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('[name=orderId]');
    input.value = '42';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    const request = pending();
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual(['orderId']);
    expect(request.request.params.get('orderId')).toBe('42');
    fixture.detectChanges();
    expect(text()).toContain('Searching trades');
    request.flush([trade]);
    fixture.detectChanges();
    expect(text()).toContain('AAPL');
    expect(text()).toContain('123.4567');
    expect(text()).toContain('2026-01-10 12:00:00 UTC');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });
  it('submits client and date inputs without a leftover order ID', async () => {
    component.orderId = '42';
    fixture.nativeElement.querySelector('[value=client]').click();
    await fixture.whenStable();
    for (const [name, value] of [['clientId', '7'], ['from', '2026-01-01'], ['to', '2026-01-31']]) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector('[name=' + name + ']');
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    const request = pending();
    expect(request.request.params.get('clientId')).toBe('7');
    expect(request.request.params.get('from')).toBe('2026-01-01');
    expect(request.request.params.get('to')).toBe('2026-01-31');
    expect(request.request.params.has('orderId')).toBe(false);
    request.flush([trade, { ...trade, orderId: 43, side: 'SELL' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(text()).toContain('SELL');
  });
  it('shows an empty state after a completed search', () => {
    expect(text()).toContain('Enter search criteria');
    component.orderId = '99'; component.search();
    pending().flush([]);
    fixture.detectChanges();
    expect(text()).toContain('No trades found');
  });
  for (const id of ['', '0', '-1', '1.5', 'abc', '2147483648']) {
    it('rejects invalid ID: ' + id, () => {
      component.orderId = id; component.search();
      expect(component.error()).toContain('valid positive');
      http.expectNone(() => true);
    });
  }
  for (const [from, to] of [['', '2026-01-01'], ['2026-01-01', ''], ['2026-02-30', '2026-03-01'], ['2026-02-02', '2026-02-01'], ['0000-01-01', '2026-01-01']]) {
    it('rejects invalid dates: ' + from + ' to ' + to, () => {
      component.mode = 'client'; component.clientId = '7'; component.from = from; component.to = to;
      component.search();
      expect(component.error()).not.toBe('');
      http.expectNone(() => true);
    });
  }
  for (const [status, message] of [[400, 'Check your search'], [401, 'session has expired'], [403, 'Access denied'], [500, 'Unable to search']] as const) {
    it('shows an error and permits retry for HTTP ' + status, () => {
      component.orderId = '42'; component.search();
      pending().flush({}, { status, statusText: 'Error' });
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain(message);
      expect(component.loading()).toBe(false);
      component.search(); pending().flush([trade]);
      expect(component.error()).toBe('');
      expect(component.results().length).toBe(1);
    });
  }
  it('cancels outdated requests and clears previous results', () => {
    component.orderId = '42'; component.search();
    const request = pending();
    component.clearResults();
    expect(request.cancelled).toBe(true);
    component.orderId = '43'; component.search(); pending().flush([trade]);
    component.clearResults();
    expect(component.results()).toEqual([]);
    expect(component.searched()).toBe(false);
  });
  it('cancels requests when leaving the page', () => {
    component.orderId = '42'; component.search();
    const request = pending(); fixture.destroy();
    expect(request.cancelled).toBe(true);
  });
});
