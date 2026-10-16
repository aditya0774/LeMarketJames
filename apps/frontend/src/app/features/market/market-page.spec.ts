import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { Quotes } from '../../core/quotes/quotes';
import { TEST_INSTRUMENTS, loadTestInstruments } from '../../core/market/instrument-catalog.testing';
import { MarketPage } from './market-page';

function quote(symbol: string, price: number) {
  return {
    symbol, name: symbol, price, priceChange: -1, priceChangePercent: -0.4, openPrice: price + 1,
    highPrice: price + 2, lowPrice: price - 2, volume: 1000, marketCap: 1e9, peRatio: 0, dividendYield: 0, lastUpdate: '',
  };
}

describe('MarketPage', () => {
  let fixture: ComponentFixture<MarketPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Auth, useValue: { currentUser: signal('lebron'), currentAccountId: signal(7) } },
        { provide: HoldingsService, useValue: { getOwnHoldings: () => of({ success: true, holdings: [] }) } },
        {
          provide: Quotes,
          useValue: {
            watchQuotes: () => of({ AAPL: quote('AAPL', 227.5), TSLA: quote('TSLA', 248.9) }),
            watchQuote: (symbol: string) => of({ success: true, quote: quote(symbol, 248.9) }),
          },
        },
      ],
    }).compileComponents();
    await loadTestInstruments();

    fixture = TestBed.createComponent(MarketPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const rows = () => Array.from(fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>);

  it('lists every stock with its live price and a trend graph', () => {
    expect(rows()).toHaveLength(TEST_INSTRUMENTS.length);
    const tsla = rows().find((r) => r.textContent?.includes('TSLA'))!;
    expect(tsla.getAttribute('aria-label')).toBe('Trade TSLA');
    expect(tsla.textContent).toContain('$248.90');
    expect(tsla.textContent).toContain('▼');
    // Seeded with [open, price], so the graph has a line straight away.
    expect(tsla.querySelector('.row-chart path')?.getAttribute('d')).toContain('L');
    expect(fixture.nativeElement.textContent).toContain(`of ${TEST_INSTRUMENTS.length} stocks`);
  });

  it('narrows the list as the user searches', () => {
    const search = fixture.nativeElement.querySelector('input[aria-label="Filter stocks"]') as HTMLInputElement;
    search.value = 'tsl';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(rows().map((r) => r.getAttribute('aria-label'))).toEqual(['Trade TSLA']);
  });

  it('opens the buy/sell popup when a stock is clicked, and closes it again', async () => {
    rows().find((r) => r.textContent?.includes('TSLA'))!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role=dialog] h2')?.textContent).toContain('Trade TSLA');

    (fixture.nativeElement.querySelector('.modal-close') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role=dialog]')).toBeNull();
  });
});
