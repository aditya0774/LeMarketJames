import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { PriceHistory } from '../../../core/history/price-history';
import { ChartPoint, ChartRange } from '../../../shared/models/chart.model';
import { PortfolioChart } from './portfolio-chart';

describe('PortfolioChart', () => {
  let fixture: ComponentFixture<PortfolioChart>;
  let requested: ChartRange[];
  let answer: () => Observable<ChartPoint[]>;
  const el = () => fixture.nativeElement as HTMLElement;

  async function setup() {
    requested = [];
    await TestBed.configureTestingModule({
      imports: [PortfolioChart],
      providers: [
        provideRouter([]),
        {
          provide: PriceHistory,
          useValue: {
            portfolio: (range: ChartRange) => {
              requested.push(range);
              return answer();
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PortfolioChart);
    fixture.detectChanges();
  }

  beforeEach(() => {
    answer = () => of([{ time: 1, value: 1000 }, { time: 2, value: 1200 }]);
  });

  it("loads the past month by default and shows the portfolio's value", async () => {
    await setup();

    expect(requested).toEqual(['1M']);
    expect(el().querySelector('.pc-value')?.textContent).toBe('$1,200.00');
  });

  it('loads another range when one is chosen, and again when the account changes', async () => {
    await setup();

    (el().querySelectorAll<HTMLButtonElement>('.pc-ranges button')[0]).click();
    fixture.detectChanges();
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();

    expect(requested).toEqual(['1M', '1D', '1D']);
  });

  it('points a user without stocks to their first trade', async () => {
    answer = () => of([]);
    await setup();

    expect(el().querySelector('.empty-state')?.textContent).toContain('Once you own a stock');
    expect(el().querySelector('.empty-state a')?.getAttribute('href')).toBe('/trade');
  });

  it('says when prices are unavailable', async () => {
    answer = () => throwError(() => new HttpErrorResponse({ status: 503 }));
    await setup();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Prices are temporarily unavailable.');
  });
});
