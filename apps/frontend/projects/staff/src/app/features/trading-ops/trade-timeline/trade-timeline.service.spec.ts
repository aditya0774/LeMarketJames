import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TradeTimelineService } from './trade-timeline.service';
import { AuditEvent } from '../../../shared/models/audit-event.model';

describe('TradeTimelineService', () => {
  let service: TradeTimelineService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(TradeTimelineService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the staff gateway for the order timeline and passes the events on', () => {
    const events: AuditEvent[] = [
      { eventType: 'SUBMITTED', occurredAt: '2026-09-21T10:30:00Z', details: { side: 'BUY', quantity: 10, price: 244.2366 } },
      { eventType: 'FILLED', occurredAt: '2026-09-21T10:30:03Z', details: { executionPrice: 244.2366, quantity: 10 } },
    ];
    let received: AuditEvent[] = [];

    service.getOrderTimeline(42).subscribe((e) => (received = e));
    const request = http.expectOne('/api/v1/orders/42/timeline');
    expect(request.request.method).toBe('GET');
    request.flush(events);

    expect(received).toEqual(events);
  });
});
