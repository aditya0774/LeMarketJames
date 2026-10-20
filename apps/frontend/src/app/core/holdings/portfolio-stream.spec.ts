import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PortfolioStream } from './portfolio-stream';

describe('PortfolioStream', () => {
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

  let refreshes: number;
  let stream: FakeEventSource;

  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    refreshes = 0;
  });

  afterEach(() => vi.unstubAllGlobals());

  function open() {
    const subscription = TestBed.inject(PortfolioStream).refreshes().subscribe(() => refreshes++);
    stream = FakeEventSource.instances[0];
    return subscription;
  }

  it('opens the portfolio stream and refreshes on each balance-changed event', () => {
    open();
    expect(stream.url).toBe(`${environment.apiBaseUrl}/api/v1/portfolio/stream`);

    stream.emitOpen();
    stream.emit('balance-changed', { accountId: 7, orderId: 42, cash: 9900, occurredAt: '' });

    expect(refreshes).toBe(1);
  });

  it('refreshes when it reconnects after dropping, but not on the first open', () => {
    open();

    stream.emitOpen();
    expect(refreshes).toBe(0);

    stream.emitError();
    stream.emitOpen();
    expect(refreshes).toBe(1);
  });

  it('closes the stream on unsubscribe', () => {
    open().unsubscribe();

    expect(stream.closed).toBe(true);
  });
});
