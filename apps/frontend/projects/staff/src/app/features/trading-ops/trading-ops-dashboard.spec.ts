import { TestBed } from '@angular/core/testing';
import { TradingOpsDashboard } from './trading-ops-dashboard';
import { provideRouter } from '@angular/router';

describe('TradingOpsDashboard', () => {
  it('renders its heading', async () => {
    await TestBed.configureTestingModule({ imports: [TradingOpsDashboard], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(TradingOpsDashboard);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Trading Ops dashboard');
  });
});
