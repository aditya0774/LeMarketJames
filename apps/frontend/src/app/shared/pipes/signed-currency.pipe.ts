import { Pipe, PipeTransform } from '@angular/core';

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** Gain/loss money with an explicit sign, as in the mockup: "+$1,204.11" / "−$38.40". */
@Pipe({ name: 'signedCurrency' })
export class SignedCurrencyPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null || Number.isNaN(value)) {
      return '—';
    }
    return (value < 0 ? '−' : '+') + usd.format(Math.abs(value));
  }
}
