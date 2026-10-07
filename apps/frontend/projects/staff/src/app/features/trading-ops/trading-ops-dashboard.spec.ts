import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TradingOpsDashboard } from './trading-ops-dashboard';
import { Auth } from '../../core/auth/auth';
import { provideRouter } from '@angular/router';
import { AuditEvent } from '../../shared/models/audit-event.model';
import { vi } from 'vitest';

describe('TradingOpsDashboard', () => {
  let httpMock: HttpTestingController;
  let authMock: Partial<Auth>;

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
    authMock = {
      hasRole: vi.fn().mockReturnValue(true),
    };

    await TestBed.configureTestingModule({
      imports: [TradingOpsDashboard],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Auth, useValue: authMock },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders its heading', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Trading Ops Dashboard');
  });

  it('displays trade search when TRADING_OPS role is granted', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('staff-trade-search')).toBeTruthy();
  });

  it('displays access denied when user lacks TRADING_OPS role', async () => {
    (authMock.hasRole as any).mockReturnValue(false);
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.hasAccess).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Access denied');
    expect(fixture.nativeElement.textContent).toContain('TRADING_OPS role');
    expect(fixture.nativeElement.querySelector('staff-trade-search')).toBeFalsy();
  });

  it('loads and displays trade timeline when trade is selected', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    // Simulate selecting a trade
    component.selectOrder(42);
    
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush(mockEvents);
    
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.events.length).toBe(2);
    expect(component.selectedOrderId).toBe(42);
  });

  it('does not load timeline on init when TRADING_OPS role is granted', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.hasAccess).toBe(true);
    expect(component.selectedOrderId).toBeNull();
    httpMock.expectNone('/api/v1/orders');
  });

  it('does not display trade search when TRADING_OPS role is denied', async () => {
    (authMock.hasRole as any).mockReturnValue(false);
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('staff-trade-search')).toBeFalsy();
  });

  it('shows loading state while fetching timeline', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    component.selectOrder(42);
    expect(component.isLoading).toBe(true);

    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush(mockEvents);
    await fixture.whenStable();

    expect(component.isLoading).toBe(false);
  });

  it('displays error message on 403 Forbidden', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    component.selectOrder(42);
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush({ message: 'Access denied' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(component.error).toContain('TRADING_OPS');
  });

  it('displays error message on 404 Not Found', async () => {
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    component.selectOrder(42);
    const req = httpMock.expectOne('/api/v1/orders/42/timeline');
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(component.error).toContain('Order not found');
  });
});
