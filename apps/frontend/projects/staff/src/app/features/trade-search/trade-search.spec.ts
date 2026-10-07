import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TradeSearch } from './trade-search';

describe('Staff order and client search', () => {
  let component: TradeSearch;
  let http: HttpTestingController;
  const trade = {orderId: 42, clientId: 7, accountId: 9, instrumentId: 1, symbol: 'MSFT', side: 'BUY', quantity: 2, pricePerUnit: 123.4567, filledAt: '2026-01-10T12:00:00'};
  beforeEach(() => {
    TestBed.configureTestingModule({imports: [TradeSearch], providers: [provideHttpClient(), provideHttpClientTesting()]});
    component = TestBed.createComponent(TradeSearch).componentInstance;
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const order = () => http.expectOne(r => r.params.has('orderId'));
  const client = () => http.expectOne(r => r.params.has('clientId'));
  for (const mode of ['order', 'client'] as const) {
    it('searches only the selected numeric ID type: ' + mode, () => {
      component.mode = mode; component.term = '42'; component.search();
      const request = mode === 'order' ? order() : client();
      expect(request.request.params.keys()).toEqual([mode === 'order' ? 'orderId' : 'clientId']);
      request.flush([trade]); expect(component.results()[0].orderId).toBe(42);
    });
    it('accepts a client name in ' + mode + ' search', () => {
      component.mode = mode; component.term = ' Bronny '; component.search();
      http.expectOne(r => r.params.get('name') === 'Bronny').flush([{clientId: 42, fullName: 'Bronny James'}]);
      client().flush([trade]); expect(component.selected()).toContain('Bronny James');
    });
  }
  it('keeps duplicate names separate until a client is selected', () => {
    component.term = 'Alex'; component.search();
    http.expectOne(r => r.params.has('name')).flush([{clientId: 7, fullName: 'Alex'}, {clientId: 8, fullName: 'Alex'}]);
    http.expectNone(r => r.params.has('clientId'));
    component.selectClient(component.clients()[1]); const request = client();
    expect(request.request.params.get('clientId')).toBe('8'); request.flush([]);
    expect(component.searched()).toBe(true);
  });
  it('applies optional dates only to the client query', () => {
    component.term = '42'; component.from = '2026-01-01'; component.to = '2026-01-31'; component.search();
    const o = order(); expect(o.request.params.has('from')).toBe(false); o.flush([]);
    component.mode = 'client'; component.search();
    const c = client(); expect(c.request.params.get('from')).toBe(component.from); c.flush([]);
    expect(component.searched()).toBe(true);
  });
  for (const [from, to] of [['2026-01-01', ''], ['', '2026-01-01'], ['2026-02-30', '2026-03-01'], ['2026-02-02', '2026-02-01']]) {
    it('rejects invalid date range ' + from + ':' + to, () => {
      component.term = '42'; component.from = from; component.to = to; component.search();
      expect(component.error()).not.toBe(''); http.expectNone(() => true);
    });
  }
  for (const term of ['', '0', '2147483648', 'a']) {
    it('rejects invalid search ' + term, () => {
      component.term = term; component.search(); expect(component.error()).not.toBe(''); http.expectNone(() => true);
    });
  }
  it('cancels pending requests when input changes', () => {
    component.term = '42'; component.search(); const o = order(); component.clearResults();
    expect(o.cancelled).toBe(true);
  });
  for (const status of [400, 401, 403, 500]) {
    it('shows errors for HTTP ' + status, () => {
      component.term = 'Bronny'; component.search();
      http.expectOne(r => r.params.has('name')).flush({}, {status, statusText: 'Failed'});
      expect(component.error()).not.toBe(''); expect(component.loading()).toBe(false);
    });
  }
});
