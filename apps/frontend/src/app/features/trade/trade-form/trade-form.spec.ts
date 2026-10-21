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

  it('steps the shares up and down, never below one, and starts again from a cleared box', () => {
    const plus = el().querySelector('button[aria-label="Increase quantity"]') as HTMLButtonElement;
    const minus = el().querySelector('button[aria-label="Decrease quantity"]') as HTMLButtonElement;
    const qty = () => (el().querySelector('#trade-qty') as HTMLInputElement).value;

    plus.click();
    plus.click();
    fixture.detectChanges();
    expect(qty()).toBe('3');
    expect(el().textContent).toContain('$250.00'); // buying power after 3 × $250 from $1,000

    minus.click();
    minus.click();
    minus.click();
    fixture.detectChanges();
    expect(qty()).toBe('1');

    setQty('');
    expect(submitButton().disabled).toBe(true);
    expect(el().textContent).not.toContain('Buying power after');
    plus.click();
    fixture.detectChanges();
    expect(qty()).toBe('1');
  });

  it('shows the first progress step for a status the ticket does not chart', () => {
    pending = new Subject();
    fixture.componentInstance.submit();
    pending.next({ success: true, orderId: 7, orderStatus: 'DELAYED' });
    pending.complete();
    fixture.detectChanges();

    expect(el().querySelectorAll('.tf-steps li.on')).toHaveLength(1);
  });

  for (const answer of [
    { body: { success: true, orderStatus: 'REJECTED', rejectionReason: 'Market closed' }, message: 'Market closed' },
    { body: { success: false }, message: 'The order was rejected.' },
  ]) {
    it(`reports a rejection: ${answer.message}`, () => {
      pending = new Subject();
      fixture.componentInstance.submit();
      pending.next(answer.body);
      pending.complete();
      fixture.detectChanges();

      expect(el().querySelector('[role=alert]')?.textContent).toContain(answer.message);
    });
  }

  for (const failure of [
    { error: new HttpErrorResponse({ status: 409, error: { message: 'Duplicate order' } }), message: 'Duplicate order' },
    { error: new HttpErrorResponse({ status: 500, error: null }), message: 'Unable to place the order.' },
    { error: new Error('Network unreachable'), message: 'Network unreachable' },
    { error: 'boom', message: 'Unable to place the order.' },
  ]) {
    it(`explains a failure: ${failure.message}`, () => {
      pending = new Subject();
      fixture.componentInstance.submit();
      pending.error(failure.error);
      fixture.detectChanges();

      expect(el().querySelector('[role=alert]')?.textContent).toContain(failure.message);
    });
  }

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

  describe('by amount (BR-04)', () => {
    const byAmount = () => {
      (el().querySelectorAll('.size-toggle button')[1] as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    const setAmount = (value: string) => {
      const amount = el().querySelector('#trade-amount') as HTMLInputElement;
      amount.value = value;
      amount.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };

    it('starts on shares', () => {
      expect(el().querySelector('#trade-qty')).not.toBeNull();
      expect(el().querySelector('#trade-amount')).toBeNull();
      expect(el().querySelector('.size-toggle [aria-pressed=true]')?.textContent).toBe('Shares');
    });

    it('shows the whole shares an amount buys and what is left over before confirming', () => {
      byAmount();
      setAmount('600');

      expect(el().querySelector('#trade-amount-shares')?.textContent).toContain('Buys 2 shares, about $100.00 left over.');
      expect(submitButton().textContent?.trim()).toBe('Buy 2 TSLA · $500.00');
      expect(submitButton().disabled).toBe(false);
    });

    it('sends the amount, not a share count, and confirms the shares the server bought', () => {
      byAmount();
      setAmount('600');
      submitButton().click();
      fixture.detectChanges();

      expect(placedBuy).toEqual([{ accountId: 7, instrumentId: 5, amount: 600, pricePerUnit: 250 }]);
      expect(el().querySelector('[role=status]')?.textContent).toContain('Buy order #42 for 2 TSLA is submitted.');
    });

    it('sells by amount through the order endpoint', () => {
      sell();
      byAmount();
      setAmount('520.50');

      expect(el().querySelector('#trade-amount-shares')?.textContent).toContain('Sells 2 shares, about $20.50 less than the amount.');
      submitButton().click();
      fixture.detectChanges();

      expect(placedSell).toEqual([{ accountId: 7, instrumentId: 5, orderType: 'SELL', amount: 520.5 }]);
    });

    it('blocks an amount below the price of one share', () => {
      byAmount();
      setAmount('249.99');

      expect(el().textContent).toContain('Amount is less than the price of one share.');
      expect(submitButton().disabled).toBe(true);
    });

    it('blocks an amount with fractions of a cent, and an empty one', () => {
      byAmount();
      expect(submitButton().disabled).toBe(true);
      expect(el().textContent).toContain('orders are for whole shares');

      setAmount('300.005');
      expect(el().textContent).toContain('Enter an amount in dollars and cents.');
      expect(submitButton().disabled).toBe(true);
    });

    it('blocks selling more shares than held by amount too', () => {
      sell();
      byAmount();
      setAmount('1000'); // 4 shares at 250; 3 held

      expect(el().textContent).toContain("You can't sell more shares than you hold.");
      expect(submitButton().disabled).toBe(true);
    });

    it('waits for the live price to say how many shares', () => {
      fixture.componentRef.setInput('quote', null);
      fixture.detectChanges();
      byAmount();
      setAmount('600');

      expect(el().textContent).toContain('The share count shows once the live price loads.');
    });

    it('keeps the way of sizing on "New trade" but clears the amount', () => {
      byAmount();
      setAmount('600');
      submitButton().click();
      fixture.detectChanges();
      (el().querySelector('.tf-actions .btn-outline') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect((el().querySelector('#trade-amount') as HTMLInputElement).value).toBe('');
    });
  });
});
