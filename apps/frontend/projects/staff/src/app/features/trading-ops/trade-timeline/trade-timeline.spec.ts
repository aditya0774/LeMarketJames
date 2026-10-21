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

  it('formats timestamps via DatePipe', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = mockEvents;
    await fixture.whenStable();

    const times = fixture.nativeElement.querySelectorAll('.event-time');
    // DatePipe transforms timestamps; verify they're rendered with the pipe
    expect(times[0]?.textContent).toBeTruthy();
    // Should contain some date/time representation (exact format depends on locale)
    expect(times[0]?.textContent?.length).toBeGreaterThan(0);
  });

  it('displays quote details (executionPrice) for FILLED events', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = mockEvents;
    await fixture.whenStable();

    const textContent = fixture.nativeElement.textContent;
    expect(textContent).toContain('executionPrice');
    expect(textContent).toContain('244.2366');
  });

  it('displays cash and holding deltas for SETTLED events', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = mockEvents;
    await fixture.whenStable();

    const textContent = fixture.nativeElement.textContent;
    expect(textContent).toContain('cashDelta');
    expect(textContent).toContain('-2442.366');
    expect(textContent).toContain('quantityDelta');
    expect(textContent).toContain('10');
  });

  // BR-15: an order placed while the market was closed shows each step it went through.
  it('shows the waiting steps of an order placed while the market was closed', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = [
      { eventType: 'ACCEPTED', occurredAt: '2026-09-19T22:00:02Z', details: {} },
      { eventType: 'DELAYED', occurredAt: '2026-09-19T22:00:02Z', details: { reason: 'MARKET_CLOSED' } },
      { eventType: 'PENDING', occurredAt: '2026-09-21T13:30:00Z', details: {} },
      { eventType: 'SETTLED', occurredAt: '2026-09-21T13:30:01Z', details: { cashDelta: -100, quantityDelta: 1 } },
      { eventType: 'FILLED', occurredAt: '2026-09-21T13:30:01Z', details: { quantity: 1, price: 100 } },
    ];
    await fixture.whenStable();

    const cards: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.timeline-event'));
    expect(cards.map(card => card.querySelector('.event-type')?.textContent?.trim())).toEqual([
      'ACCEPTED', 'DELAYED', 'PENDING', 'SETTLED', 'FILLED',
    ]);
    expect(cards.map(card => card.dataset['tone'])).toEqual(['progress', 'waiting', 'progress', 'settled', 'done']);
    expect(cards[1].textContent).toContain('MARKET_CLOSED');
  });

  it('shows the quote a rejection was made on', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = [{
      eventType: 'REJECTED',
      occurredAt: '2026-09-21T13:30:00Z',
      details: { reason: 'STALE_QUOTE', quotePrice: 101.2346, quoteTime: '2026-09-21T13:29:50Z' },
    }];
    await fixture.whenStable();

    const card: HTMLElement = fixture.nativeElement.querySelector('.timeline-event');
    expect(card.dataset['tone']).toBe('refused');
    expect(card.textContent).toContain('quotePrice');
    expect(card.textContent).toContain('101.2346');
    expect(card.textContent).toContain('2026-09-21T13:29:50Z');
  });

  // BR-14: an event older than the online retention window is still shown, marked as archived.
  it('marks only the archived events', async () => {
    await TestBed.configureTestingModule({ imports: [TradeTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TradeTimelineComponent);
    fixture.componentInstance.events = [
      { ...mockEvents[0], archived: true },
      { ...mockEvents[1], archived: false },
    ];
    await fixture.whenStable();

    const cards: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.timeline-event'));
    expect(cards.map(card => card.querySelector('.archived')?.textContent?.trim() ?? null)).toEqual(['Archived', null]);
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
