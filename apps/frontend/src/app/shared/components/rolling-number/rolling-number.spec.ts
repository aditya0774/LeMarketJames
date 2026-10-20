import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RollingNumber, rollingCells } from './rolling-number';

describe('rollingCells', () => {
  it('rolls digits and draws everything else as is', () => {
    expect(rollingCells('−$1.5%').map((c) => c.digit)).toEqual([null, null, 1, null, 5, null]);
  });

  it('keys each cell by its place from the right', () => {
    expect(rollingCells('$9.99').map((c) => c.place)).toEqual([5, 4, 3, 2, 1]);
  });
});

describe('RollingNumber', () => {
  let fixture: ComponentFixture<RollingNumber>;
  const el = () => fixture.nativeElement as HTMLElement;
  const digits = () => Array.from(el().querySelectorAll<HTMLElement>('.rn-digit'));

  function show(text: string): void {
    fixture.componentRef.setInput('text', text);
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(RollingNumber);
    show('$999.99');
  });

  it('reads as the plain value, without the digit strips', () => {
    expect(el().textContent?.trim()).toBe('$999.99');
    expect(el().querySelector('.rn')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('sets each digit column to its digit', () => {
    expect(digits().map((d) => d.style.getPropertyValue('--rn-digit'))).toEqual(['9', '9', '9', '9', '9']);
  });

  it('keeps the same column elements when the number grows, so they roll instead of being replaced', () => {
    const cents = digits().at(-1);

    show('$1,000.00');

    expect(el().textContent?.trim()).toBe('$1,000.00');
    expect(digits().at(-1)).toBe(cents);
    expect(cents?.style.getPropertyValue('--rn-digit')).toBe('0');
  });
});
