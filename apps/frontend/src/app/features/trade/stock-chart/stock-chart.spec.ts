import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { PriceHistory } from '../../../core/history/price-history';
import { ChartRange } from '../../../shared/models/chart.model';
import { Quote } from '../../../shared/models/quote.model';
import { StockChart, withLivePrice } from './stock-chart';

const T0 = Date.parse('2026-10-15T13:31:00Z');

function quote(price: number, lastUpdate: string): Quote {
  return {
    symbol: 'AAPL', name: 'Apple', price, priceChange: 0, priceChangePercent: 0, highPrice: price, lowPrice: price,
    openPrice: price, volume: 0, marketCap: 0, peRatio: 0, dividendYield: 0, lastUpdate,
  };
}

describe('withLivePrice', () => {
  const points = [{ time: T0, value: 230 }];

  it('adds a newer live quote to 1D', () => {
    expect(withLivePrice(points, '1D', quote(231, '2026-10-15T13:32:00Z'))).toEqual([
      { time: T0, value: 230 },
      { time: T0 + 60_000, value: 231 },
    ]);
  });

  it('ignores a quote no newer than the last point', () => {
    expect(withLivePrice(points, '1D', quote(231, '2026-10-15T13:30:00Z'))).toEqual(points);
    expect(withLivePrice(points, '1D', quote(231, ''))).toEqual(points);
  });

  it("makes the live price today's close on longer ranges", () => {
    expect(withLivePrice(points, '1M', quote(232, '2026-10-15T13:32:00Z'))).toEqual([{ time: T0, value: 232 }]);
  });

  it('leaves an empty series empty', () => {
    expect(withLivePrice([], '1D', quote(231, '2026-10-15T13:32:00Z'))).toEqual([]);
  });
});

describe('StockChart', () => {
  let fixture: ComponentFixture<StockChart>;
  let requests: string[];
  let answer: () => Observable<{ time: number; value: number }[]>;

  beforeEach(async () => {
    requests = [];
    answer = () => of([{ time: T0, value: 230 }]);
    await TestBed.configureTestingModule({
      imports: [StockChart],
      providers: [
        {
          provide: PriceHistory,
          useValue: {
            stock: (symbol: string, range: ChartRange) => {
              requests.push(`${symbol} ${range}`);
              return answer();
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(StockChart);
    fixture.componentRef.setInput('symbol', 'AAPL');
    fixture.detectChanges();
  });

  it("loads today's prices for the stock, then whichever range and stock come next", () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h2')?.textContent).toBe('AAPL price');

    (el.querySelectorAll<HTMLButtonElement>('.pc-ranges button')[2]).click();
    fixture.detectChanges();
    fixture.componentRef.setInput('symbol', 'MSFT');
    fixture.detectChanges();

    expect(requests).toEqual(['AAPL 1D', 'AAPL 1M', 'MSFT 1M']);
  });

  it('says when the chart cannot load, in words of its own for a feed outage', () => {
    const el = fixture.nativeElement as HTMLElement;
    answer = () => throwError(() => new HttpErrorResponse({ status: 503 }));
    (el.querySelectorAll<HTMLButtonElement>('.pc-ranges button')[1]).click();
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('The chart needs live prices and is back once they are.');

    answer = () => throwError(() => new HttpErrorResponse({ status: 500 }));
    (el.querySelectorAll<HTMLButtonElement>('.pc-ranges button')[2]).click();
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong. Try again in a moment.');
  });

  it('waits without an error while prices are down, then loads again', () => {
    const el = fixture.nativeElement as HTMLElement;
    fixture.componentRef.setInput('pricesDown', true);
    fixture.detectChanges();

    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelector('.empty-state')?.textContent).toContain('back as soon as live prices are');

    fixture.componentRef.setInput('pricesDown', false);
    fixture.detectChanges();
    expect(requests).toEqual(['AAPL 1D', 'AAPL 1D']);
    expect(el.querySelector('.pc-line')).not.toBeNull();
  });
});
