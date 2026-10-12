import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TradeTimelineService } from './trade-timeline.service';
import { firstValueFrom } from 'rxjs';
import { AuditEvent } from '../../../shared/models/audit-event.model';

describe('TradeTimelineService', () => {
  let service: TradeTimelineService;
  let httpMock: HttpTestingController;

  const mockEvents: AuditEvent[] = [
    {
      eventType: 'SUBMITTED',
      occurredAt: '2026-09-21T10:30:00Z',
      details: { side: 'BUY', quantity: 10, price: 244.2366 },
    },
    {
      eventType: 'FILLED',
      occurredAt: '2026-09-21T10:30:03Z',
      details: { executionPrice: 244.2366, quantity: 10 },
    },
    {
      eventType: 'SETTLED',
      occurredAt: '2026-09-21T10:30:04Z',
      details: { cashDelta: -2442.366, quantityDelta: 10 },
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TradeTimelineService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TradeTimelineService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call the correct endpoint with orderId', () => {
    const orderId = 42;
    service.getOrderTimeline(orderId).subscribe();

    const req = httpMock.expectOne(`/api/v1/orders/${orderId}/timeline`);
    expect(req.request.method).toBe('GET');
    req.flush(mockEvents);
  });

  it('should return audit events in chronological order', async () => {
    const orderId = 42;
    const promise = firstValueFrom(service.getOrderTimeline(orderId));

    const req = httpMock.expectOne(`/api/v1/orders/${orderId}/timeline`);
    req.flush(mockEvents);

    const events = await promise;
    expect(events.length).toBe(3);
    expect(events[0]?.eventType).toBe('SUBMITTED');
    expect(events[2]?.eventType).toBe('SETTLED');
  });

  it('should include all event details from backend response', async () => {
    const orderId = 42;
    const promise = firstValueFrom(service.getOrderTimeline(orderId));

    const req = httpMock.expectOne(`/api/v1/orders/${orderId}/timeline`);
    req.flush(mockEvents);

    const events = await promise;
    const settled = events.find((e) => e.eventType === 'SETTLED');
    expect(settled?.details['cashDelta']).toBe(-2442.366);
    expect(settled?.details['quantityDelta']).toBe(10);
  });

  it('should handle 403 Forbidden (access denied)', async () => {
    const orderId = 42;
    const promise = firstValueFrom(service.getOrderTimeline(orderId));

    const req = httpMock.expectOne(`/api/v1/orders/${orderId}/timeline`);
    req.flush({ message: 'Access denied' }, { status: 403, statusText: 'Forbidden' });

    try {
      await promise;
      expect(true).toBe(false); // should not reach here
    } catch (error: any) {
      expect(error.status).toBe(403);
    }
  });

  it('should handle 404 Not Found', async () => {
    const orderId = 999;
    const promise = firstValueFrom(service.getOrderTimeline(orderId));

    const req = httpMock.expectOne(`/api/v1/orders/${orderId}/timeline`);
    req.flush({ message: 'Order not found' }, { status: 404, statusText: 'Not Found' });

    try {
      await promise;
      expect(true).toBe(false); // should not reach here
    } catch (error: any) {
      expect(error.status).toBe(404);
    }
  });
});
