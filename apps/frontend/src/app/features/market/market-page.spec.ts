import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Auth } from '../../core/auth/auth';
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
        {
          provide: Quotes,
          useValue: {
            watchQuotes: () => of({ AAPL: quote('AAPL', 227.5), TSLA: quote('TSLA', 248.9) }),
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

  it('carries the trend graphs on when the user leaves the tab and comes back', async () => {
    const points = () =>
      rows().find((r) => r.textContent?.includes('TSLA'))!.querySelector('.row-chart path')!.getAttribute('d')!.split(' ').length;
    expect(points()).toBe(2);

    // Switching tabs destroys the page; coming back makes a new one.
    fixture.destroy();
    fixture = TestBed.createComponent(MarketPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(points()).toBe(3);
  });

  it('narrows the list as the user searches', () => {
    const search = fixture.nativeElement.querySelector('input[aria-label="Filter stocks"]') as HTMLInputElement;
    search.value = 'tsl';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(rows().map((r) => r.getAttribute('aria-label'))).toEqual(['Trade TSLA']);
  });

  it('opens the trade page for a stock when its row is selected', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    rows().find((r) => r.textContent?.includes('TSLA'))!.click();

    expect(navigate).toHaveBeenCalledWith(['/trade'], { queryParams: { symbol: 'TSLA' } });
  });

  describe('watchlist', () => {
    const panel = () => fixture.nativeElement.querySelector('app-watchlist-panel') as HTMLElement;
    const star = (symbol: string) =>
      fixture.nativeElement.querySelector(`button.star[aria-label$="${symbol} to watchlist"], button.star[aria-label$="${symbol} from watchlist"]`) as HTMLButtonElement | null;

    async function answerWatchlist(body: unknown, status = 200) {
      TestBed.inject(HttpTestingController)
        .expectOne('/api/v1/watchlist')
        .flush(body, { status, statusText: status === 200 ? 'OK' : 'Not Found' });
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('lists the watched stocks with their prices, and stars them in the stock list', async () => {
      await answerWatchlist({ success: true, watchlist: [{ symbol: 'TSLA', addedAt: '' }] });

      expect(panel().querySelector('.count')?.textContent).toBe('1');
      expect(panel().querySelector('li')?.textContent).toContain('TSLA');
      expect(panel().querySelector('li')?.textContent).toContain('$248.90');
      expect(star('TSLA')?.getAttribute('aria-pressed')).toBe('true');
      expect(star('TSLA')?.getAttribute('aria-label')).toBe('Remove TSLA from watchlist');
      expect(star('AAPL')?.getAttribute('aria-pressed')).toBe('false');
    });

    it('adds a starred stock without opening its trade page', async () => {
      await answerWatchlist({ success: true, watchlist: [] });
      expect(panel().textContent).toContain('Nothing on your watchlist');
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      star('AAPL')!.click();
      fixture.detectChanges();

      expect(panel().textContent).toContain('AAPL');
      expect(navigate).not.toHaveBeenCalled();
      const post = TestBed.inject(HttpTestingController).expectOne('/api/v1/watchlist');
      expect(post.request.body).toEqual({ symbol: 'AAPL' });
    });

    it('removes a stock from the panel', async () => {
      await answerWatchlist({ success: true, watchlist: [{ symbol: 'TSLA', addedAt: '' }] });

      (panel().querySelector('button[aria-label="Remove TSLA from watchlist"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(panel().textContent).toContain('Nothing on your watchlist');
      expect(TestBed.inject(HttpTestingController).expectOne('/api/v1/watchlist/TSLA').request.method).toBe('DELETE');
    });

    it('says the watchlist could not load, with no stars, when it cannot be read', async () => {
      await answerWatchlist(null, 404);

      expect(panel().textContent).toContain("Couldn't load your watchlist");
      expect(star('TSLA')).toBeNull();
    });
  });
});
