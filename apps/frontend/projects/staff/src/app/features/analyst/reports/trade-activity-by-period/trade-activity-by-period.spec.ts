import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TradeActivityByPeriod } from './trade-activity-by-period';

describe('TradeActivityByPeriod', () => {
  it('renders its heading', async () => {
    await TestBed.configureTestingModule({
      imports: [TradeActivityByPeriod],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(TradeActivityByPeriod);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Trade activity by period');
  });
});
