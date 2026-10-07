import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TradingOpsDashboard } from './trading-ops-dashboard';
import { provideRouter } from '@angular/router';
import { AuditEvent } from '../../shared/models/audit-event.model';

describe('TradingOpsDashboard', () => {
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
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TradingOpsDashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders its heading', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush(mockEvents);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Trading Ops Dashboard');
  });

  it('displays trade search placeholder', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush(mockEvents);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Click a trade result to view its timeline below');
  });

  it('loads and displays trade timeline on init', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush(mockEvents);
    await fixture.whenStable();

    // Timeline should be displayed with hardcoded orderId 42
    expect(fixture.nativeElement.textContent).toContain('Order Timeline #42');
    expect(fixture.nativeElement.querySelector('staff-trade-timeline')).toBeTruthy();
  });

  it('sets selectedOrderId on init', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush(mockEvents);

    await fixture.whenStable();

    expect(component.selectedOrderId).toBe(42);
  });

  it('shows loading state while fetching timeline', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    // At this point, isLoading should be true
    expect(component.isLoading).toBe(true);

    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush(mockEvents);
    await fixture.whenStable();

    expect(component.isLoading).toBe(false);
  });

  it('displays error message on 403 Forbidden', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush({ message: 'Access denied' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    const component = fixture.componentInstance;
    expect(component.error).toContain('TRADING_OPS');
  });

  it('displays error message on 404 Not Found', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    const component = fixture.componentInstance;
    expect(component.error).toContain('Order not found');
  });
});
