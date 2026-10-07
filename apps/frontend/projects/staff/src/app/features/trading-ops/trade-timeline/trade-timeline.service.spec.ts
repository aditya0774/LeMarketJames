import { TestBed } from '@angular/core/testing';
import { TradeTimelineService } from './trade-timeline.service';
import { firstValueFrom } from 'rxjs';

describe('TradeTimelineService', () => {
  let service: TradeTimelineService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TradeTimelineService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return audit events in chronological order', async () => {
    const events = await firstValueFrom(service.getOrderTimeline(42));
    expect(events.length).toBe(5);
    expect(events[0]?.eventType).toBe('SUBMITTED');
    expect(events[4]?.eventType).toBe('SETTLED');
  });

  it('should include all event details from contract C6', async () => {
    const events = await firstValueFrom(service.getOrderTimeline(42));
    const submitted = events.find((e) => e.eventType === 'SUBMITTED');
    expect(submitted?.details['side']).toBe('BUY');
    expect(submitted?.details['quantity']).toBe(10);
    expect(submitted?.details['price']).toBe(244.2366);

    const settled = events.find((e) => e.eventType === 'SETTLED');
    expect(settled?.details['cashDelta']).toBe(-2442.366);
    expect(settled?.details['quantityDelta']).toBe(10);
  });

  it('should return events with ISO-8601 UTC timestamps', async () => {
    const events = await firstValueFrom(service.getOrderTimeline(42));
    events.forEach((event) => {
      expect(event.occurredAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/);
    });
  });
});
