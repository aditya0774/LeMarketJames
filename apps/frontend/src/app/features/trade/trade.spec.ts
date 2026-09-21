import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { OrderRequest, OrderService } from '../../core/orders/order.service';
import { Quotes } from '../../core/quotes/quotes';
import { Trade } from './trade';

const quote = {
  symbol: 'TSLA', name: 'Tesla Inc', price: 250, priceChange: 5, priceChangePercent: 2.04,
  highPrice: 252, lowPrice: 244, openPrice: 246, volume: 88_100_000, marketCap: 792_600_000_000,
  peRatio: 0, dividendYield: 0, lastUpdate: '',
};

describe('Trade', () => {
  let fixture: ComponentFixture<Trade>;
  let placed: OrderRequest[];

  beforeEach(async () => {
    placed = [];
    await TestBed.configureTestingModule({
      imports: [Trade],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ symbol: 'tsla' })) } },
        { provide: Auth, useValue: { currentUser: signal('lebron'), currentAccountId: signal(7) } },
        { provide: Quotes, useValue: { watchQuote: () => of({ success: true, quote }) } },
        {
          provide: HoldingsService,
          useValue: { getOwnHoldings: () => of({ success: true, holdings: [{ symbol: 'TSLA', quantity: 3 }] }) },
        },
        {
          provide: OrderService,
          useValue: {
            createOrder: (req: OrderRequest) => {
              placed.push(req);
              return of({ orderId: 42, orderStatus: 'FILLED' });
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Trade);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const submitButton = () => el().querySelector('button[type=submit]') as HTMLButtonElement;
  const render = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };

  it('shows the live quote and estimated total', async () => {
    (el().querySelector('[aria-label="Increase quantity"]') as HTMLButtonElement).click();
    await render();

    expect(el().querySelector('h1')?.textContent).toContain('Trade TSLA');
    expect(el().querySelector('.price-now')?.textContent).toContain('$250.00');
    expect(el().querySelector('.breakdown-row.total')?.textContent).toContain('$500.00');
    expect(el().textContent).toContain('You currently hold 3 shares of TSLA');
  });

  it('places a BUY market order with the catalog instrument id', async () => {
    submitButton().click();
    await render();

    expect(placed).toEqual([{ accountId: 7, instrumentId: 5, orderType: 'BUY', quantity: 1 }]);
    expect(el().querySelector('.alert.ok')?.textContent).toContain('#42');
  });

  it('blocks selling more shares than held', async () => {
    (el().querySelectorAll('.type-toggle .opt')[1] as HTMLButtonElement).click();
    const qty = el().querySelector('#trade-qty') as HTMLInputElement;
    qty.value = '4';
    qty.dispatchEvent(new Event('input'));
    await render();

    expect(submitButton().disabled).toBe(true);
    expect(el().textContent).toContain("You can't sell more shares than you hold.");
  });
});
