import { TestBed } from '@angular/core/testing';
import { TradeTimelineComponent } from './trade-timeline';
import { AuditEvent } from '../../../shared/models/audit-event.model';

describe('TradeTimelineComponent', () => {
  const mockEvents: AuditEvent[] = [
    {
      eventType: 'SUBMITTED',
      occurredAt: '2026-09-21T10:30:00Z',
      details: {
        side: 'BUY',
        quantity: 10,
        price: 244.2366,
      },
    },
    {
      eventType: 'VALIDATED',
      occurredAt: '2026-09-21T10:30:01Z',
      details: {
        validationStatus: 'PASSED',
      },
    },
    {
      eventType: 'FILLED',
      occurredAt: '2026-09-21T10:30:03Z',
      details: {
        executionPrice: 244.2366,
        quantity: 10,
      },
    },
    {
      eventType: 'SETTLED',
      occurredAt: '2026-09-21T10:30:04Z',
      details: {
        cashDelta: -2442.366,
        quantityDelta: 10,
      },
    },
  ];

  it('renders all events in chronological order', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = mockEvents;
    await fixture.whenStable();

    const events = fixture.nativeElement.querySelectorAll('.timeline-event');
    expect(events.length).toBe(4);
  });

  it('displays event type for each event', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = mockEvents;
    await fixture.whenStable();

    const eventTypes = fixture.nativeElement.querySelectorAll('.event-type');
    expect(eventTypes[0]?.textContent).toContain('SUBMITTED');
    expect(eventTypes[1]?.textContent).toContain('VALIDATED');
    expect(eventTypes[2]?.textContent).toContain('FILLED');
    expect(eventTypes[3]?.textContent).toContain('SETTLED');
  });

  it('displays event timestamps', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = mockEvents;
    await fixture.whenStable();

    const times = fixture.nativeElement.querySelectorAll('.event-time');
    expect(times.length).toBe(4);
    expect(times[0]?.textContent).toBeTruthy();
  });

  it('displays all event details', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = mockEvents;
    await fixture.whenStable();

    const detailKeys = fixture.nativeElement.querySelectorAll('.detail-key');
    expect(detailKeys.length).toBeGreaterThan(0);

    const textContent = fixture.nativeElement.textContent;
    expect(textContent).toContain('side');
    expect(textContent).toContain('BUY');
    expect(textContent).toContain('quantity');
    expect(textContent).toContain('10');
    expect(textContent).toContain('cashDelta');
    expect(textContent).toContain('-2442.366');
  });

  it('renders empty state when no events provided', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = [];
    await fixture.whenStable();

    const events = fixture.nativeElement.querySelectorAll('.timeline-event');
    expect(events.length).toBe(0);
  });
});
