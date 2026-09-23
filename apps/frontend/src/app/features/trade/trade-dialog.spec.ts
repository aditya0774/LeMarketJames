import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, Subject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { BuyOrderRequest, OrderRequest, OrderService } from '../../core/orders/order.service';
import { Quotes } from '../../core/quotes/quotes';
import { TradeDialog } from './trade-dialog';

const quote = {
  symbol: 'TSLA', name: 'Tesla Inc', price: 250, priceChange: 5, priceChangePercent: 2.04,
  highPrice: 252, lowPrice: 244, openPrice: 246, volume: 88_100_000, marketCap: 792_600_000_000,
  peRatio: 0, dividendYield: 0, lastUpdate: '',
};

describe('TradeDialog', () => {
  let fixture: ComponentFixture<TradeDialog>;
  let placedSell: OrderRequest[];
  let placedBuy: BuyOrderRequest[];
  let closedCount: number;
  let placedEvents: number;
  let pending: Subject<any> | null;

  beforeEach(async () => {
    placedSell = [];
    placedBuy = [];
    closedCount = 0;
    placedEvents = 0;
    pending = null;
    await TestBed.configureTestingModule({
      imports: [TradeDialog],
      providers: [
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
              placedSell.push(req);
              return pending ?? of({ success: true, orderId: 42, orderStatus: 'SUBMITTED' });
            },
            submitBuyOrder: (req: BuyOrderRequest) => {
              placedBuy.push(req);
              return pending ?? of({ success: true, orderId: 42, orderStatus: 'SUBMITTED' });
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TradeDialog);
    fixture.componentRef.setInput('symbol', 'tsla');
    fixture.componentRef.setInput('initialHistory', [246, 248]);
    fixture.componentInstance.closed.subscribe(() => closedCount++);
    fixture.componentInstance.orderPlaced.subscribe(() => placedEvents++);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const submitButton = () => el().querySelector('button[type=submit]') as HTMLButtonElement;
  const render = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };

  it('shows the live quote, the seeded chart and the estimated total', async () => {
    (el().querySelector('[aria-label="Increase quantity"]') as HTMLButtonElement).click();
    await render();

    expect(el().querySelector('[role=dialog]')).toBeTruthy();
    expect(el().querySelector('h2')?.textContent).toContain('Trade TSLA');
    expect(el().querySelector('.price-now')?.textContent).toContain('$250.00');
    expect(el().querySelector('.mini-chart path')?.getAttribute('d')).toMatch(/^M.* L.* L/); // 246, 248, 250
    expect(el().querySelector('.breakdown-row.total')?.textContent).toContain('$500.00');
    expect(el().textContent).toContain('You currently hold 3 shares of TSLA');
  });

  it('places a BUY market order with the catalog instrument id and tells the dashboard', async () => {
    submitButton().click();
    await render();

    expect(placedSell).toEqual([]);
    expect(placedBuy).toEqual([{ accountId: 7, instrumentId: 5, quantity: 1, pricePerUnit: 250 }]);
    expect(el().querySelector('.alert.ok')?.textContent).toContain('#42');
    expect(el().querySelector('.alert.ok')?.textContent).toContain('submitted');
    expect(placedEvents).toBe(1);
  });

  it('blocks duplicate submissions while waiting for the backend', async () => {
    pending = new Subject();
    fixture.componentInstance.submit();
    fixture.componentInstance.submit();
    await render();
    expect(placedBuy.length).toBe(1);
    expect(placedSell).toEqual([]);
    expect(submitButton().disabled).toBe(true);
    expect(submitButton().textContent).toContain('Placing order');
    pending.next({ success: true, orderId: 99, orderStatus: 'SUBMITTED' });
    pending.complete();
    await render();
    expect(submitButton().disabled).toBe(false);
    expect(el().querySelector('[role=status]')?.textContent).toContain('#99');
  });

  for (const failure of [
    { status: 400, error: { reason: 'Insufficient balance', code: 'INSUFFICIENT_CASH' }, message: 'Insufficient balance' },
    { status: 400, error: { reason: 'Market price is unavailable', code: 'PRICE_UNAVAILABLE' }, message: 'Market price is unavailable' },
    { status: 401, error: {}, message: 'Your session has expired' },
    { status: 0, error: {}, message: 'Unable to place the order' },
  ]) {
    it(`recovers after a ${failure.message} error without confirming an order`, async () => {
      pending = new Subject();
      fixture.componentInstance.submit();
      pending.error(new HttpErrorResponse(failure));
      await render();
      expect(el().querySelector('[role=alert]')?.textContent).toContain(failure.message);
      expect(el().querySelector('.alert.ok')).toBeNull();
      expect(placedEvents).toBe(0);
      expect(submitButton().disabled).toBe(false);
    });
  }

  it('blocks selling more shares than held', async () => {
    (el().querySelectorAll('.type-toggle .opt')[1] as HTMLButtonElement).click();
    const qty = el().querySelector('#trade-qty') as HTMLInputElement;
    qty.value = '4';
    qty.dispatchEvent(new Event('input'));
    await render();

    expect(submitButton().disabled).toBe(true);
    expect(el().textContent).toContain("You can't sell more shares than you hold.");
  });

  it('closes on Escape, the × button and a backdrop click, but not a click inside', () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    (el().querySelector('.modal-close') as HTMLButtonElement).click();
    (el().querySelector('.modal-backdrop') as HTMLElement).click();
    (el().querySelector('.modal') as HTMLElement).click();

    expect(closedCount).toBe(3);
  });
});
