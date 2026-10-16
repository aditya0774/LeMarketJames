import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Movers, biggestMovers } from './movers';

const q = (symbol: string, priceChangePercent: number) => ({
  symbol, name: symbol, price: 10, priceChange: priceChangePercent, priceChangePercent, openPrice: 10,
  highPrice: 10, lowPrice: 10, volume: 0, marketCap: 0, peRatio: 0, dividendYield: 0, lastUpdate: '',
});

describe('biggestMovers', () => {
  it('ranks by size of move either way and skips missing quotes', () => {
    const ranked = biggestMovers({ A: q('A', 1), B: q('B', -4), C: null, D: q('D', 2) }, 2);

    expect(ranked.map((m) => m.symbol)).toEqual(['B', 'D']);
  });
});

describe('Movers', () => {
  it('waits for prices before listing anything', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Movers);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role=status]').textContent).toContain('Waiting for live prices');
  });
});
