import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { CashService } from '../../../core/cash/cash.service';
import { BalanceInfo } from '../../../shared/models/order.model';
import { MoveMoney, checkAmount } from './move-money';

describe('checkAmount', () => {
  it('waits while the box is empty', () => {
    expect(checkAmount('  ', 'DEPOSIT', 100)).toEqual({ amount: null, error: null });
  });

  it('accepts dollars and cents, with a $ sign and commas', () => {
    expect(checkAmount('$1,250.50', 'DEPOSIT', null)).toEqual({ amount: 1250.5, error: null });
  });

  it('refuses anything else, nothing, and more than the buying power for a withdrawal', () => {
    expect(checkAmount('12.345', 'DEPOSIT', null).error).toBe('Enter an amount in dollars and cents, like 250.00.');
    expect(checkAmount('abc', 'DEPOSIT', null).error).toBe('Enter an amount in dollars and cents, like 250.00.');
    expect(checkAmount('0', 'DEPOSIT', null).error).toBe('Enter an amount above $0.');
    expect(checkAmount('500.01', 'WITHDRAW', 500).error).toBe('You can withdraw up to $500.00.');
    expect(checkAmount('500', 'WITHDRAW', 500).amount).toBe(500);
    // A deposit has no upper bound here; the server enforces the per-movement limit.
    expect(checkAmount('500.01', 'DEPOSIT', 500).amount).toBe(500.01);
  });
});

describe('MoveMoney', () => {
  let fixture: ComponentFixture<MoveMoney>;
  let calls: { kind: string; amount: number; requestId: string }[];
  let answer: () => Promise<unknown>;
  const el = () => fixture.nativeElement as HTMLElement;
  const balance = (buyingPower: number) => ({ buyingPower }) as BalanceInfo;

  async function open(start: 'DEPOSIT' | 'WITHDRAW' = 'DEPOSIT', buyingPower: number | null = 1000) {
    calls = [];
    await TestBed.configureTestingModule({
      imports: [MoveMoney],
      providers: [
        {
          provide: CashService,
          useValue: {
            move: (kind: string, amount: number, requestId: string) => {
              calls.push({ kind, amount, requestId });
              return answer();
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MoveMoney);
    fixture.componentRef.setInput('start', start);
    fixture.componentRef.setInput('buyingPower', buyingPower);
    fixture.detectChanges();
  }

  function type(text: string) {
    const box = el().querySelector('#cash-amount') as HTMLInputElement;
    box.value = text;
    box.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  const go = () => el().querySelector('.mm-go') as HTMLButtonElement;

  async function submit() {
    go().click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    answer = () => Promise.resolve({ success: true, balance: balance(1250) });
  });

  it('deposits, shows the new buying power and reports the new balance', async () => {
    await open();
    const moved: BalanceInfo[] = [];
    fixture.componentInstance.moved.subscribe((b) => moved.push(b));

    expect(el().querySelector('[role="dialog"]')?.getAttribute('aria-labelledby')).toBe('mm-title');
    expect(go().disabled).toBe(true);
    type('250');
    expect(go().textContent).toContain('Deposit $250.00');
    expect(el().querySelector('.mm-power .after')?.textContent).toContain('$1,250.00');

    await submit();

    expect(calls).toEqual([{ kind: 'DEPOSIT', amount: 250, requestId: expect.any(String) }]);
    expect(el().querySelector('output.mm-done')?.textContent).toContain('Deposit of $250.00 added');
    expect(el().querySelector('output.mm-done')?.textContent).toContain('Your buying power is now $1,250.00.');
    expect(moved).toEqual([balance(1250)]);
  });

  it('offers quick amounts, with All for a withdrawal, and stops a withdrawal above the buying power', async () => {
    await open('WITHDRAW', 800);

    const quick = Array.from(el().querySelectorAll<HTMLButtonElement>('.mm-quick button'));
    expect(quick.map((b) => b.textContent?.trim())).toEqual(['$500', '$1,000', 'All']);
    quick[2].click();
    fixture.detectChanges();
    expect((el().querySelector('#cash-amount') as HTMLInputElement).value).toBe('800.00');
    expect(go().disabled).toBe(false);

    quick[1].click();
    fixture.detectChanges();
    expect(el().querySelector('.mm-err')?.textContent).toBe('You can withdraw up to $800.00.');
    expect(go().disabled).toBe(true);
  });

  it("says when moving money isn't available yet", async () => {
    answer = () => Promise.reject(new HttpErrorResponse({ status: 404 }));
    await open();
    type('100');

    await submit();

    expect(el().querySelector('.mm-err')?.textContent).toBe("Deposits and withdrawals aren't available yet.");
  });

  it("shows the server's reason, and resends an unconfirmed attempt with the same request id", async () => {
    answer = () => Promise.reject(new HttpErrorResponse({ status: 400, error: { error: 'The most you can deposit at once is $50,000.00.' } }));
    await open();
    type('60000');
    await submit();
    expect(el().querySelector('.mm-err')?.textContent).toBe('The most you can deposit at once is $50,000.00.');

    answer = () => Promise.reject(new HttpErrorResponse({ status: 0 }));
    type('100');
    await submit();
    await submit();

    expect(el().querySelector('.mm-err')?.textContent).toContain("it won't be applied twice");
    // The changed amount got a new id; its retry kept it.
    expect(calls[1].requestId).not.toBe(calls[0].requestId);
    expect(calls[2].requestId).toBe(calls[1].requestId);
  });

  it('switches between deposit and withdraw', async () => {
    await open();
    const [deposit, withdraw] = Array.from(el().querySelectorAll<HTMLButtonElement>('.mm-kind button'));

    withdraw.click();
    fixture.detectChanges();

    expect(withdraw.getAttribute('aria-pressed')).toBe('true');
    expect(deposit.getAttribute('aria-pressed')).toBe('false');
    expect(el().querySelector('.mm-note')?.textContent).toContain('Removed from your buying power');
  });

  it('closes on Escape and on the close button', async () => {
    await open();
    let closes = 0;
    fixture.componentInstance.closed.subscribe(() => closes++);

    // Heard from anywhere on the page, since focus can leave the dialog while it is sending.
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    (el().querySelector('.mm-close') as HTMLButtonElement).click();

    expect(closes).toBe(2);
  });
});
