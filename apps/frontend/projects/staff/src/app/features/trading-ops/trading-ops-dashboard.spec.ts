import { TestBed } from '@angular/core/testing';
import { TradingOpsDashboard } from './trading-ops-dashboard';

describe('TradingOpsDashboard', () => {
  it('renders its heading', async () => {
    await TestBed.configureTestingModule({ imports: [TradingOpsDashboard] }).compileComponents();
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Trading Ops dashboard');
  });

  it('displays trade search placeholder', async () => {
    await TestBed.configureTestingModule({ imports: [TradingOpsDashboard] }).compileComponents();
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Trade search will appear here when LMKT-139 lands');
  });

  it('loads and displays trade timeline on init', async () => {
    await TestBed.configureTestingModule({ imports: [TradingOpsDashboard] }).compileComponents();
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    await fixture.whenStable();

    // Timeline should be displayed with hardcoded orderId 42
    expect(fixture.nativeElement.textContent).toContain('Order Timeline #42');
    expect(fixture.nativeElement.querySelector('staff-trade-timeline')).toBeTruthy();
  });

  it('sets selectedOrderId on init', async () => {
    await TestBed.configureTestingModule({ imports: [TradingOpsDashboard] }).compileComponents();
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    const component = fixture.componentInstance;
    
    await fixture.whenStable();

    expect(component.selectedOrderId).toBe(42);
  });
});
