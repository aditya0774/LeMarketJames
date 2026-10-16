import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { Auth } from '../../../core/auth/auth';
import { BuyOrderRequest, OrderRequest, OrderService } from '../../../core/orders/order.service';
import { Instrument } from '../../../core/market/instrument-catalog';
import { TradeForm } from './trade-form';

const tsla: Instrument = { instrumentId: 5, symbol: 'TSLA', name: 'TesLe', tradable: true };
const quote = {
  symbol: 'TSLA', name: 'TesLe', price: 250, priceChange: 5, priceChangePercent: 2.04,
  highPrice: 252, lowPrice: 244, openPrice: 246, volume: 1, marketCap: 1, peRatio: 0, dividendYield: 0, lastUpdate: '',
};

describe('TradeForm', () => {
  let fixture: ComponentFixture<TradeForm>;
  let placedBuy: BuyOrderRequest[];
  let placedSell: OrderRequest[];
  let pending: Subject<any> | null;
  let placedEvents: number;
  let accountId: number | null;

  beforeEach(() => {
    placedBuy = [];
    placedSell = [];
    pending = null;
    placedEvents = 0;
    accountId = 7;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: Auth, useValue: { currentAccountId: () => accountId } },
        {
          provide: OrderService,
          useValue: {
            submitBuyOrder: (req: BuyOrderRequest) => {
              placedBuy.push(req);
              return pending ?? of({ success: true, orderId: 42, orderStatus: 'SUBMITTED' });
            },
            createOrder: (req: OrderRequest) => {
              placedSell.push(req);
              return pending ?? of({ success: true, orderId: 99, orderStatus: 'ACCEPTED' });
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(TradeForm);
    fixture.componentRef.setInput('instrument', tsla);
    fixture.componentRef.setInput('quote', quote);
    fixture.componentRef.setInput('held', 3);
    fixture.componentRef.setInput('buyingPower', 1000);
    fixture.componentInstance.orderPlaced.subscribe(() => placedEvents++);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const submitButton = () => el().querySelector('button[type=submit]') as HTMLButtonElement;
  const sell = () => (el().querySelectorAll('.type-toggle .opt')[1] as HTMLButtonElement).click();
  const setQty = (value: string) => {
    const qty = el().querySelector('#trade-qty') as HTMLInputElement;
    qty.value = value;
    qty.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  it('prices the order and names it on the button', () => {
    (el().querySelector('[aria-label="Increase quantity"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(submitButton().textContent?.trim()).toBe('Buy 2 TSLA · $500.00');
    expect(el().textContent).toContain('$500.00');
    expect(el().textContent).toContain('Buying power after');
    expect(el().textContent).toContain('$500.00'); // 1000 − 500
    expect(el().textContent).toContain('You own 3 shares of TSLA.');
  });

  it('places a buy at the live price and confirms it with its progress', () => {
    submitButton().click();
    fixture.detectChanges();

    expect(placedBuy).toEqual([{ accountId: 7, instrumentId: 5, quantity: 1, pricePerUnit: 250 }]);
    const status = el().querySelector('[role=status]')!;
    expect(status.textContent).toContain('Buy order #42 for 1 TSLA is submitted.');
    expect(status.querySelectorAll('.tf-steps li.on')).toHaveLength(1);
    expect(placedEvents).toBe(1);
    expect(submitButton()).toBeNull();
  });

  it('starts a new ticket with "New trade"', () => {
    submitButton().click();
    fixture.detectChanges();

    (Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.trim() === 'New trade') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el().querySelector('[role=status]')).toBeNull();
    expect(submitButton()).not.toBeNull();
  });

  it('blocks duplicate submissions while waiting for the backend', () => {
    pending = new Subject();
    fixture.componentInstance.submit();
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(placedBuy.length).toBe(1);
    expect(submitButton().disabled).toBe(true);
    expect(submitButton().textContent).toContain('Placing order');
  });

  for (const failure of [
    { status: 400, error: { reason: 'Insufficient balance' }, message: 'Insufficient balance' },
    { status: 401, error: {}, message: 'Your session has expired' },
    { status: 0, error: {}, message: 'Unable to place the order' },
  ]) {
    it(`recovers after a ${failure.message} error without confirming an order`, () => {
      pending = new Subject();
      fixture.componentInstance.submit();
      pending.error(new HttpErrorResponse(failure));
      fixture.detectChanges();

      expect(el().querySelector('[role=alert]')?.textContent).toContain(failure.message);
      expect(el().querySelector('[role=status]')).toBeNull();
      expect(submitButton().disabled).toBe(false);
      expect(placedEvents).toBe(0);
    });
  }

  it('sells through the holdings-checked endpoint', () => {
    sell();
    fixture.detectChanges();
    submitButton().click();
    fixture.detectChanges();

    expect(placedSell).toEqual([{ accountId: 7, instrumentId: 5, orderType: 'SELL', quantity: 1 }]);
    expect(el().querySelector('[role=status]')?.textContent).toContain('Sell order #99 for 1 TSLA is accepted.');
    expect(el().querySelectorAll('.tf-steps li.on')).toHaveLength(2);
  });

  it('does not confirm an order rejected in the response body', () => {
    pending = new Subject();
    sell();
    fixture.componentInstance.submit();
    pending.next({ success: false, reason: 'Insufficient holdings' });
    pending.complete();
    fixture.detectChanges();

    expect(el().querySelector('[role=alert]')?.textContent).toContain('Insufficient holdings');
    expect(placedEvents).toBe(0);
  });

  it('blocks selling more shares than held', () => {
    sell();
    setQty('4');

    expect(submitButton().disabled).toBe(true);
    expect(el().textContent).toContain("You can't sell more shares than you hold.");
  });

  it('blocks a quantity that is not a whole share', () => {
    setQty('1.5');

    expect(submitButton().disabled).toBe(true);
    expect(el().textContent).toContain('Enter a whole number of shares (at least 1).');
  });

  it('blocks a stock that is not tradable', () => {
    fixture.componentRef.setInput('instrument', { ...tsla, tradable: false });
    fixture.detectChanges();

    expect(submitButton().disabled).toBe(true);
    expect(el().textContent).toContain('TSLA is not currently tradable.');
  });

  it('blocks ordering while the price feed is failing', () => {
    fixture.componentRef.setInput('quoteError', true);
    fixture.detectChanges();

    expect(submitButton().disabled).toBe(true);
  });

  it('does not price a buy without a live quote', () => {
    fixture.componentRef.setInput('quote', null);
    fixture.detectChanges();
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(placedBuy).toEqual([]);
    expect(el().querySelector('[role=alert]')?.textContent).toContain('Live pricing is unavailable');
  });

  it('refuses without a trading account', () => {
    accountId = null;
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(el().querySelector('[role=alert]')?.textContent).toContain('No trading account is linked to this login.');
  });

  it('says when holdings are unknown', () => {
    fixture.componentRef.setInput('held', null);
    fixture.detectChanges();

    expect(el().textContent).toContain("Your current TSLA position couldn't be loaded.");
  });

  it('starts fresh when the stock changes', () => {
    setQty('3');
    fixture.componentRef.setInput('instrument', { ...tsla, instrumentId: 6, symbol: 'AAPL' });
    fixture.detectChanges();

    expect((el().querySelector('#trade-qty') as HTMLInputElement).value).toBe('1');
  });
});
