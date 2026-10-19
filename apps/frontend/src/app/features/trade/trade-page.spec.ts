import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { BalanceService } from '../../core/orders/orders.service';
import { OrderService } from '../../core/orders/order.service';
import { Quotes } from '../../core/quotes/quotes';
import { PriceHistory } from '../../core/history/price-history';
import { loadTestInstruments } from '../../core/market/instrument-catalog.testing';
import { TradePage } from './trade-page';

const quote = {
  symbol: 'TSLA', name: 'TesLe', price: 250, priceChange: 5, priceChangePercent: 2.04,
  highPrice: 252, lowPrice: 244, openPrice: 246, volume: 88_100_000, marketCap: 792_600_000_000,
  peRatio: 0, dividendYield: 0, lastUpdate: '',
};

describe('TradePage', () => {
  let fixture: ComponentFixture<TradePage>;
  let params: BehaviorSubject<Record<string, string>>;
  let quoteResult: () => Observable<unknown>;
  let holdingsCalls: number;
  let balanceCalls: number;

  async function setup(symbol: string | null = 'tsla') {
    params = new BehaviorSubject<Record<string, string>>(symbol ? { symbol } : {});
    holdingsCalls = 0;
    balanceCalls = 0;
    await TestBed.configureTestingModule({
      imports: [TradePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: new Observable((s) => params.subscribe((p) => s.next(convertToParamMap(p)))) } },
        { provide: Auth, useValue: { currentUser: signal('lebron'), currentAccountId: signal(7) } },
        { provide: Quotes, useValue: { watchQuote: () => quoteResult() } },
        // Today's chart: the open, then a later price; the live quote is added after them.
        { provide: PriceHistory, useValue: { stock: () => of([{ time: 1, value: 246 }, { time: 2, value: 248 }]) } },
        {
          provide: HoldingsService,
          useValue: {
            getOwnHoldings: () => {
              holdingsCalls++;
              return of({ success: true, holdings: [{ symbol: 'TSLA', quantity: 3 }] });
            },
          },
        },
        {
          provide: BalanceService,
          useValue: {
            getBalance: () => {
              balanceCalls++;
              return Promise.resolve({ success: true, balance: { buyingPower: 5000 } });
            },
          },
        },
        { provide: OrderService, useValue: { submitBuyOrder: () => of({ success: true, orderId: 42, orderStatus: 'SUBMITTED' }) } },
      ],
    }).compileComponents();
    await loadTestInstruments();

    fixture = TestBed.createComponent(TradePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    quoteResult = () => of({ success: true, quote });
  });

  it('shows the live quote, its chart, the day range and the figures', async () => {
    await setup();

    expect(el().querySelector('h1')?.textContent).toContain('Trade TSLA');
    expect(el().querySelector('.price-now')?.textContent).toContain('$250.00');
    expect(el().querySelector('.pc-line')?.getAttribute('d')).toMatch(/^M.* L/);
    expect(el().querySelector('app-stock-chart h2')?.textContent).toContain('TSLA price');
    expect(el().querySelector('.range-head')?.textContent).toContain('$244.00');
    expect(el().querySelector('.range-head')?.textContent).toContain('$252.00');
    expect(el().querySelector('.stats')?.textContent).toContain('88.1M');
    expect(el().querySelector('.stats')?.textContent).toContain('$245.00'); // previous close = price − change
    expect(el().textContent).toContain('You own 3 shares of TSLA.');
  });

  it('marks the stocks the user owns in the picker', async () => {
    await setup();

    const options = Array.from((el().querySelector('.pick select') as HTMLSelectElement).options);
    expect(options.find((o) => o.value === 'TSLA')?.textContent).toContain('(you own 3)');
    expect((el().querySelector('.pick select') as HTMLSelectElement).value).toBe('TSLA');
  });

  it('puts a newly picked stock in the URL', async () => {
    await setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const select = el().querySelector('.pick select') as HTMLSelectElement;

    select.value = 'NVDA';
    select.dispatchEvent(new Event('change'));

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { symbol: 'NVDA' }, replaceUrl: true }));
  });

  it('opens on the first stock when the URL names none', async () => {
    await setup(null);

    expect(el().querySelector('h1')?.textContent?.trim()).toMatch(/^Trade \w+$/);
  });

  it('says so for a symbol that does not exist', async () => {
    await setup('nope');

    expect(el().querySelector('[role=alert]')?.textContent).toContain('Stock not found');
    expect(el().querySelector('app-trade-form')).toBeNull();
  });

  it('shows the prices-unavailable banner and blocks ordering while the feed fails', async () => {
    quoteResult = () => throwError(() => new HttpErrorResponse({ status: 503 }));
    await setup();

    expect(el().querySelector('[role=alert]')?.textContent).toContain('Prices are temporarily unavailable');
    expect((el().querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('reloads holdings and buying power after an order', async () => {
    await setup();
    const before = { holdings: holdingsCalls, balance: balanceCalls };

    (el().querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el().querySelector('[role=status]')?.textContent).toContain('Buy order #42 for 1 TSLA is submitted.');
    expect(holdingsCalls).toBeGreaterThan(before.holdings);
    expect(balanceCalls).toBeGreaterThan(before.balance);
  });
});
