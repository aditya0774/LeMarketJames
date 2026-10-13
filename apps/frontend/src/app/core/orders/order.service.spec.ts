import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { Auth } from '../auth/auth';
import { HoldingsService } from '../holdings/holdings.service';
import { OrderService } from './order.service';

describe('BUY order HTTP integration', () => {
  class FakeEventSource {
    static instances: FakeEventSource[] = [];

    readonly listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>();
    onopen: ((this: EventSource, ev: Event) => any) | null = null;
    onerror: ((this: EventSource, ev: Event) => any) | null = null;
    closed = false;

    constructor(readonly url: string) {
      FakeEventSource.instances.push(this);
    }

    addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void {
      this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
    }

    close(): void {
      this.closed = true;
    }

    emitOpen(): void {
      this.onopen?.call(this as unknown as EventSource, new Event('open'));
    }

    emitError(): void {
      this.onerror?.call(this as unknown as EventSource, new Event('error'));
    }

    emit(type: string, data: unknown): void {
      const event = { data: JSON.stringify(data) } as MessageEvent<string>;
      for (const listener of this.listeners.get(type) ?? []) {
        listener(event);
      }
    }

    emitRaw(type: string, raw: string): void {
      const event = { data: raw } as MessageEvent<string>;
      for (const listener of this.listeners.get(type) ?? []) {
        listener(event);
      }
    }
  }

  let http: HttpTestingController;
  let service: OrderService;

  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(), OrderService,
      { provide: Auth, useValue: {} },
      { provide: HoldingsService, useValue: {} },
    ] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(OrderService);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  it('posts a typed BUY without a client price and returns the created order', () => {
    const request = { accountId: 7, instrumentId: 5, orderType: 'BUY' as const, quantity: 2 };
    let receivedId: number | undefined;
    service.createOrder(request).subscribe(order => receivedId = order.orderId);
    const call = http.expectOne(`${environment.apiBaseUrl}/api/v1/orders`);
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    call.flush({ success: true, orderId: 42, orderStatus: 'SUBMITTED' }, { status: 201, statusText: 'Created' });
    expect(receivedId).toBe(42);
  });

  it('preserves backend error details for the form', () => {
    let code: string | undefined;
    service.createOrder({ accountId: 7, instrumentId: 5, orderType: 'BUY', quantity: 2 })
      .subscribe({ error: error => code = error.error.code });
    http.expectOne(`${environment.apiBaseUrl}/api/v1/orders`)
      .flush({ success: false, code: 'INSUFFICIENT_CASH' }, { status: 400, statusText: 'Bad Request' });
    expect(code).toBe('INSUFFICIENT_CASH');
  });
  it('sends a local period and zone and normalizes UTC placement timestamps', () => {
    let submittedAt = '';
    service.getOrdersByAccountId(7, { date: '2026-03', timeZone: 'America/New_York' })
      .subscribe(orders => submittedAt = orders[0].submittedAt);
    const call = http.expectOne(request => request.url.endsWith('/account/7'));
    expect(call.request.params.get('date')).toBe('2026-03');
    expect(call.request.params.get('timeZone')).toBe('America/New_York');
    call.flush([{ submittedAt: '2026-03-08T05:00:00' }]);
    expect(submittedAt).toBe('2026-03-08T05:00:00Z');
  });

  it('omits date parameters when cleared and preserves explicit offsets', () => {
    let submittedAt = '';
    service.getOrdersByAccountId(7).subscribe(orders => submittedAt = orders[0].submittedAt);
    const call = http.expectOne(`${environment.apiBaseUrl}/api/v1/orders/account/7`);
    expect(call.request.params.keys()).toEqual([]);
    call.flush([{ submittedAt: '2026-01-01T00:00:00+05:30' }]);
    expect(submittedAt).toBe('2026-01-01T00:00:00+05:30');
  });

  it('maps C6-compatible history fields and normalizes UTC-like timestamps', () => {
    let history: any[] = [];
    service.getOrdersByAccountId(7, { date: '2026-10-12', timeZone: 'UTC' })
      .subscribe(orders => history = orders);

    const call = http.expectOne(request => request.url.endsWith('/account/7'));
    expect(call.request.method).toBe('GET');
    expect(call.request.params.get('date')).toBe('2026-10-12');
    expect(call.request.params.get('timeZone')).toBe('UTC');

    call.flush([
      {
        success: true,
        orderId: 501,
        accountId: 7,
        instrumentId: 5,
        symbol: 'AAPL',
        orderType: 'BUY',
        quantity: 10,
        pricePerUnit: 201.55,
        orderStatus: 'FILLED',
        rejectionReason: null,
        submittedAt: '2026-10-12T13:45:00',
        acceptedAt: '2026-10-12T13:45:01',
        filledAt: '2026-10-12T13:45:03',
        createdAt: '2026-10-12T13:45:00',
        updatedAt: '2026-10-12T13:45:03',
      },
      {
        success: true,
        orderId: 502,
        accountId: 7,
        instrumentId: 8,
        orderType: 'SELL',
        quantity: 2,
        pricePerUnit: null,
        orderStatus: 'REJECTED',
        rejectionReason: 'INSUFFICIENT_HOLDINGS',
        submittedAt: '2026-10-12T09:15:00Z',
        acceptedAt: null,
        filledAt: null,
        createdAt: '2026-10-12T09:15:00',
        updatedAt: '2026-10-12T09:15:00',
      },
    ]);

    expect(history).toHaveLength(2);
    expect(history[0].symbol).toBe('AAPL');
    expect(history[0].submittedAt).toBe('2026-10-12T13:45:00Z');
    expect(history[1].submittedAt).toBe('2026-10-12T09:15:00Z');
    expect(history[1].rejectionReason).toBe('INSUFFICIENT_HOLDINGS');
  });

  it('aggregates paginated account history until the backend returns a short page', () => {
    let history: any[] = [];
    service.getAllOrdersByAccountId(7).subscribe(orders => history = orders);

    const page0 = http.expectOne(request =>
      request.url.endsWith('/account/7')
      && request.params.get('page') === '0'
      && request.params.get('size') === '20');
    page0.flush(Array.from({ length: 20 }, (_, i) => ({ submittedAt: `2026-03-08T05:${String(i).padStart(2, '0')}:00`, orderId: i + 1 })));

    const page1 = http.expectOne(request =>
      request.url.endsWith('/account/7')
      && request.params.get('page') === '1'
      && request.params.get('size') === '20');
    page1.flush([
      { submittedAt: '2026-03-08T06:00:00', orderId: 21 },
      { submittedAt: '2026-03-08T06:01:00', orderId: 22 },
    ]);

    expect(history.map(order => order.orderId)).toEqual(Array.from({ length: 22 }, (_, i) => i + 1));
    expect(history[0].submittedAt).toBe('2026-03-08T05:00:00Z');
    expect(history[21].submittedAt).toBe('2026-03-08T06:01:00Z');
  });

  it('passes date and timeZone through every paginated history request', () => {
    let history: any[] = [];
    service.getAllOrdersByAccountId(7, { date: '2026-03-08', timeZone: 'America/New_York' })
      .subscribe(orders => history = orders);

    const first = http.expectOne(request =>
      request.url.endsWith('/account/7')
      && request.params.get('page') === '0'
      && request.params.get('size') === '20');
    expect(first.request.params.get('date')).toBe('2026-03-08');
    expect(first.request.params.get('timeZone')).toBe('America/New_York');
    first.flush(Array.from({ length: 20 }, (_, i) => ({ submittedAt: `2026-03-08T05:${String(i).padStart(2, '0')}:00`, orderId: i + 1 })));

    const second = http.expectOne(request =>
      request.url.endsWith('/account/7')
      && request.params.get('page') === '1'
      && request.params.get('size') === '20');
    expect(second.request.params.get('date')).toBe('2026-03-08');
    expect(second.request.params.get('timeZone')).toBe('America/New_York');
    second.flush([{ submittedAt: '2026-03-08T06:00:00', orderId: 21 }]);

    expect(history).toHaveLength(21);
  });

  it('requests one explicit history page with page-size-date-timeZone contract params', () => {
    let history: any[] = [];
    service.getOrdersByAccountIdPage(7, { page: 2, size: 6 }, { date: '2026-11', timeZone: 'America/New_York' })
      .subscribe(orders => history = orders);

    const call = http.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/api/v1/orders/account/7`
      && request.params.get('page') === '2'
      && request.params.get('size') === '6');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.get('date')).toBe('2026-11');
    expect(call.request.params.get('timeZone')).toBe('America/New_York');

    call.flush([
      {
        orderId: 700,
        symbol: 'AAPL',
        orderType: 'BUY',
        quantity: 9,
        orderStatus: 'FILLED',
        pricePerUnit: 222.01,
        rejectionReason: null,
        submittedAt: '2026-11-02T03:00:00',
      },
      {
        orderId: 701,
        symbol: 'TSLA',
        orderType: 'SELL',
        quantity: 2,
        orderStatus: 'REJECTED',
        pricePerUnit: null,
        rejectionReason: 'INSUFFICIENT_HOLDINGS',
        submittedAt: '2026-11-02T04:00:00Z',
      },
    ]);

    expect(history).toHaveLength(2);
    expect(history[0].submittedAt).toBe('2026-11-02T03:00:00Z');
    expect(history[0].symbol).toBe('AAPL');
    expect(history[1].rejectionReason).toBe('INSUFFICIENT_HOLDINGS');
  });

  it('streams account orders and marks reconnecting after a transient failure', async () => {
    vi.useFakeTimers();
    try {
      const states: string[] = [];
      const sizes: number[] = [];
      service.watchOrdersByAccountId(7, 2000).subscribe(snapshot => {
        states.push(snapshot.connection);
        sizes.push(snapshot.orders.length);
      });

      await vi.advanceTimersByTimeAsync(0);
      http.expectOne(`${environment.apiBaseUrl}/api/v1/orders/account/7`)
        .flush([{ submittedAt: '2026-03-08T05:00:00' }]);
      expect(states.at(-1)).toBe('live');
      expect(sizes.at(-1)).toBe(1);

      await vi.advanceTimersByTimeAsync(2000);
      http.expectOne(`${environment.apiBaseUrl}/api/v1/orders/account/7`)
        .flush({ message: 'temporary' }, { status: 503, statusText: 'Service Unavailable' });
      expect(states.at(-1)).toBe('reconnecting');
      expect(sizes.at(-1)).toBe(1);

      await vi.advanceTimersByTimeAsync(2000);
      http.expectOne(`${environment.apiBaseUrl}/api/v1/orders/account/7`)
        .flush([{ submittedAt: '2026-03-08T05:01:00' }]);
      expect(states.at(-1)).toBe('live');
      expect(sizes.at(-1)).toBe(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('opens the SSE stream and emits live snapshots on order-status events', () => {
    const states: string[] = [];
    const changed: Array<{ orderId: number; to: string }> = [];

    const subscription = service.watchOrderStatusStream().subscribe((snapshot) => {
      states.push(snapshot.connection);
      if (snapshot.event) {
        changed.push({ orderId: snapshot.event.orderId, to: snapshot.event.to });
      }
    });

    expect(FakeEventSource.instances).toHaveLength(1);
    const stream = FakeEventSource.instances[0];
    expect(stream.url).toBe(`${environment.apiBaseUrl}/api/v1/orders/stream`);
    expect(states[0]).toBe('connecting');

    stream.emitOpen();
    expect(states.at(-1)).toBe('live');

    stream.emit('order-status-changed', {
      orderId: 42,
      accountId: 7,
      from: 'SUBMITTED',
      to: 'FILLED',
      occurredAt: '2026-10-08T08:30:00Z',
    });
    expect(states.at(-1)).toBe('live');
    expect(changed).toEqual([{ orderId: 42, to: 'FILLED' }]);

    subscription.unsubscribe();
    expect(stream.closed).toBe(true);
  });

  it('emits reconnecting when the SSE stream errors', () => {
    const states: string[] = [];

    const subscription = service.watchOrderStatusStream().subscribe((snapshot) => {
      states.push(snapshot.connection);
    });

    const stream = FakeEventSource.instances[0];
    stream.emitOpen();
    stream.emitError();

    expect(states).toEqual(['connecting', 'live', 'reconnecting']);
    subscription.unsubscribe();
  });

  it('ignores malformed SSE payloads and keeps listening for the next valid event', () => {
    const changed: Array<{ orderId: number; to: string }> = [];

    const subscription = service.watchOrderStatusStream().subscribe((snapshot) => {
      if (snapshot.event) {
        changed.push({ orderId: snapshot.event.orderId, to: snapshot.event.to });
      }
    });

    const stream = FakeEventSource.instances[0];
    stream.emitOpen();
    stream.emitRaw('order-status-changed', '{bad json');
    stream.emit('order-status-changed', {
      orderId: 99,
      accountId: 7,
      from: 'SUBMITTED',
      to: 'REJECTED',
      occurredAt: '2026-10-08T10:00:00Z',
    });

    expect(changed).toEqual([{ orderId: 99, to: 'REJECTED' }]);
    subscription.unsubscribe();
  });
});
