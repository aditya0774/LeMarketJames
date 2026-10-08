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

    expect(FakeEventSource.instances.length).toBe(1);
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
});
