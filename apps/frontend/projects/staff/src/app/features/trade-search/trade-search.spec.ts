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
  describe('on screen', () => {
    const render = () => {
      const fixture = TestBed.createComponent(TradeSearch);
      component = fixture.componentInstance;
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;
      const refresh = () => { fixture.componentRef.changeDetectorRef.markForCheck(); fixture.detectChanges(); };
      return {el, refresh};
    };
    const status = (el: HTMLElement) => el.querySelector('.ts-status')?.textContent?.trim();

    it('asks for criteria, then reports searching and how many trades it found', () => {
      const {el, refresh} = render();
      expect(status(el)).toBe('Enter search criteria to find a trade.');
      expect(el.querySelector('label[for="ts-term"]')?.textContent).toBe('Order ID or client name');

      component.term = '42'; component.search(); refresh();
      expect(status(el)).toBe('Searching trades…');
      expect(el.querySelector('button[type="submit"]')?.textContent).toBe('Searching…');

      order().flush([trade]); refresh();
      expect(status(el)).toBe('1 trade(s) found. Select an order to see its full timeline.');
      expect(el.querySelector('.ts-selected')?.textContent).toBe('Order ID 42');
    });

    it('says when nothing matched, and shows an error in place of the hint', () => {
      const {el, refresh} = render();
      component.mode = 'client'; component.term = '42'; component.search(); client().flush([]); refresh();
      expect(status(el)).toBe('No trades found for these search criteria.');
      // The client's account status shows even without trades (BR-03).
      http.expectOne(r => r.url.endsWith('/api/v1/clients/42/status'))
        .flush({clientId: 42, fullName: 'Bronny James', status: 'ACTIVE', changes: []}); refresh();
      expect(el.querySelector('staff-client-status')?.textContent).toContain('Bronny James (Client ID 42)');
      expect(el.querySelector('label[for="ts-term"]')?.textContent).toBe('Client ID or client name');

      component.term = ''; component.search(); refresh();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain('Enter an order ID, client ID or client name.');
      expect(status(el)).toBe('');
    });

    it('lists matching clients to choose from, or says there are none', () => {
      const {el, refresh} = render();
      component.term = 'Alex'; component.search();
      http.expectOne(r => r.params.has('name')).flush([{clientId: 7, fullName: 'Alex A'}, {clientId: 8, fullName: 'Alex B'}]); refresh();

      const choices = Array.from(el.querySelectorAll<HTMLButtonElement>('.ts-clients button'));
      expect(el.querySelector('.ts-clients p')?.textContent).toBe('Choose a matching client:');
      expect(choices.map((b) => b.textContent?.trim())).toEqual(['Alex A (ID: 7)', 'Alex B (ID: 8)']);
      choices[1].click();
      client().flush([]);

      component.term = 'Nobody'; component.search();
      http.expectOne(r => r.params.has('name')).flush([]); refresh();
      expect(el.querySelector('.ts-clients p')?.textContent).toBe('No matching clients found.');
    });

    it("shows the chosen client's account status, and none for an order search", () => {
      const {el, refresh} = render();
      component.term = '42'; component.search(); order().flush([trade]); refresh();
      expect(el.querySelector('staff-client-status')).toBeNull();
      http.expectNone(r => r.url.includes('/api/v1/clients/'));

      component.term = 'Alex'; component.search();
      http.expectOne(r => r.params.has('name')).flush([{clientId: 7, fullName: 'Alex A'}]);
      client().flush([trade]); refresh();
      http.expectOne(r => r.url.endsWith('/api/v1/clients/7/status'))
        .flush({clientId: 7, fullName: 'Alex A', status: 'SUSPENDED', changes: []}); refresh();
      expect(el.querySelector('staff-client-status .cs-head .pill')?.textContent).toContain('Suspended');

      component.clearResults(); refresh();
      expect(el.querySelector('staff-client-status')).toBeNull();
    });

    it('says when only the first 50 matching clients are shown', () => {
      const {el, refresh} = render();
      component.term = 'Al'; component.search();
      http.expectOne(r => r.params.has('name'))
        .flush(Array.from({length: 50}, (_, i) => ({clientId: i + 1, fullName: `Al ${i + 1}`}))); refresh();

      expect(el.querySelector('.ts-clients .hint')?.textContent).toContain('Showing the first 50 matches.');
    });
  });

  describe('order timeline', () => {
    const events = [
      {eventType: 'SUBMITTED', occurredAt: '2026-01-10T11:59:58Z', details: {side: 'BUY', quantity: 2}},
      {eventType: 'FILLED', occurredAt: '2026-01-10T12:00:00Z', details: {executionPrice: 123.4567}},
    ];
    const render = () => {
      const fixture = TestBed.createComponent(TradeSearch);
      fixture.componentInstance.term = '42';
      fixture.componentInstance.search();
      http.expectOne(r => r.params.has('orderId')).flush([trade]);
      fixture.detectChanges();
      return fixture;
    };
    const timeline = () => http.expectOne('/api/v1/orders/42/timeline');

    it('loads the audit trail of the order picked in the results', () => {
      const fixture = render();
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('.ts-timeline')?.textContent).toContain('Select an order number');

      (el.querySelector('.order-btn') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(el.querySelector('.ts-timeline output')).not.toBeNull();

      timeline().flush(events);
      fixture.detectChanges();
      expect(el.querySelector('.order-btn')?.getAttribute('aria-pressed')).toBe('true');
      expect(el.querySelector('.ts-timeline h2')?.textContent).toContain('#42');
      expect(Array.from(el.querySelectorAll('.event-type')).map(e => e.textContent)).toEqual(['SUBMITTED', 'FILLED']);
      expect(el.querySelector('.event-time')?.textContent).toBe('2026-01-10 11:59:58 UTC');
    });

    it('says so when the order has no audit events', () => {
      const fixture = render();
      fixture.componentInstance.showTimeline(42);
      timeline().flush([]);
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('No audit events are recorded for this order.');
    });

    for (const [status, message] of [[403, 'Access denied'], [401, 'session has expired'], [500, 'Unable to load']] as const) {
      it('explains a timeline failure with HTTP ' + status, () => {
        const fixture = render();
        fixture.componentInstance.showTimeline(42);
        timeline().flush({}, {status, statusText: 'Failed'});
        fixture.detectChanges();

        expect(fixture.componentInstance.timelineError()).toContain(message);
        expect((fixture.nativeElement as HTMLElement).querySelector('.ts-timeline [role=alert]')).not.toBeNull();
      });
    }

    it('drops the timeline when a new search starts', () => {
      const fixture = render();
      fixture.componentInstance.showTimeline(42);
      const pending = timeline();
      fixture.componentInstance.clearResults();

      expect(pending.cancelled).toBe(true);
      expect(fixture.componentInstance.selectedOrder()).toBeNull();
    });
  });

});
