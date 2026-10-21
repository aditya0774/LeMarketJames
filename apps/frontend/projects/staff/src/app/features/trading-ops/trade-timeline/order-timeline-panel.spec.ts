import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrderTimelinePanel } from './order-timeline-panel';

describe('OrderTimelinePanel', () => {
  let fixture: ComponentFixture<OrderTimelinePanel>;
  let httpMock: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderTimelinePanel);
  });

  afterEach(() => httpMock.verify());

  function show(orderId: number | null) {
    fixture.componentRef.setInput('orderId', orderId);
    fixture.detectChanges();
  }

  it('asks for nothing until an order is chosen', () => {
    fixture.componentRef.setInput('prompt', 'Pick one.');
    show(null);

    expect(el().textContent).toContain('Pick one.');
  });

  it("loads the order's audit events and shows them", async () => {
    show(42);
    expect(el().textContent).toContain('Loading the timeline');

    httpMock.expectOne('/api/v1/orders/42/timeline').flush([
      { eventType: 'SUBMITTED', occurredAt: '2026-10-08T14:30:00Z', details: {} },
      { eventType: 'FILLED', occurredAt: '2026-10-08T14:30:02Z', details: {} },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el().querySelector('h2')!.textContent).toContain('#42');
    expect(el().querySelectorAll('staff-trade-timeline li')).toHaveLength(2);
  });

  it('a newer choice replaces one still loading', async () => {
    show(42);
    const first = httpMock.expectOne('/api/v1/orders/42/timeline');
    show(43);
    httpMock.expectOne('/api/v1/orders/43/timeline').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(first.cancelled).toBe(true);
    expect(el().textContent).toContain('No audit events are recorded for this order.');
  });

  it('explains a refusal', async () => {
    show(42);
    httpMock.expectOne('/api/v1/orders/42/timeline').flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')!.textContent).toContain('Order timelines require Trading Operations');
  });
});
