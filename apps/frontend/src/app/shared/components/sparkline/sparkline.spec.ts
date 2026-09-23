import { sparklinePath } from './sparkline';
import { CompactNumberPipe } from '../../pipes/compact-number.pipe';
import { SignedCurrencyPipe } from '../../pipes/signed-currency.pipe';

describe('sparklinePath', () => {
  it('needs at least two points', () => {
    expect(sparklinePath([])).toBe('');
    expect(sparklinePath([10])).toBe('');
  });

  it('puts the lowest value at the bottom and the highest at the top', () => {
    expect(sparklinePath([1, 3], 100, 20)).toBe('M0.0,16.0 L100.0,4.0');
  });

  it('draws a flat series across the middle', () => {
    expect(sparklinePath([5, 5, 5], 100, 20)).toBe('M0.0,10.0 L50.0,10.0 L100.0,10.0');
  });
});

describe('display pipes', () => {
  it('formats compact numbers', () => {
    const pipe = new CompactNumberPipe();
    expect(pipe.transform(6_200_000)).toBe('6.2M');
    expect(pipe.transform(null)).toBe('—');
  });

  it('formats signed currency', () => {
    const pipe = new SignedCurrencyPipe();
    expect(pipe.transform(1204.11)).toBe('+$1,204.11');
    expect(pipe.transform(-38.4)).toBe('−$38.40');
  });
});
