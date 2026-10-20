import { Quote } from '../../../shared/models/quote.model';
import { appendQuotes } from './market-history';

const q = (price: number, openPrice = 100) => ({ price, openPrice }) as Quote;

describe('appendQuotes', () => {
  it('seeds a new stock with today\'s open and the current price', () => {
    expect(appendQuotes({}, { AAPL: q(105) })).toEqual({ AAPL: [100, 105] });
  });

  it('appends later prices and caps the length', () => {
    const next = appendQuotes({ AAPL: [100, 101, 102] }, { AAPL: q(103) }, 3);
    expect(next).toEqual({ AAPL: [101, 102, 103] });
  });

  it('keeps a stock\'s trace when its quote failed this round', () => {
    expect(appendQuotes({ TSLA: [200, 201] }, { TSLA: null })).toEqual({ TSLA: [200, 201] });
  });

  it('does not mutate the previous history', () => {
    const before = { AAPL: [100, 101] };
    appendQuotes(before, { AAPL: q(102) });
    expect(before).toEqual({ AAPL: [100, 101] });
  });
});
