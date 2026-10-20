import { TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { Quote } from '../../shared/models/quote.model';
import { PriceHistory } from '../history/price-history';
import { MarketHistory, Trace, appendQuotes, seedTraces } from './market-history';

const AT_10_00 = '2026-09-16T14:00:05Z';
const AT_10_00_LATER = '2026-09-16T14:00:50Z';
const AT_10_01 = '2026-09-16T14:01:02Z';
const minute = (iso: string) => Math.floor(Date.parse(iso) / 60_000);
const q = (price: number, lastUpdate = AT_10_00, openPrice = 100) => ({ price, openPrice, lastUpdate }) as Quote;

describe('appendQuotes', () => {
  it("seeds a new stock with today's open and the current price", () => {
    expect(appendQuotes({}, { AAPL: q(105) })).toEqual({ AAPL: { points: [100, 105], minute: minute(AT_10_00) } });
  });

  it('moves the last point while the quote is from the same minute, and adds one when the minute changes', () => {
    let history = appendQuotes({}, { AAPL: q(105) });
    history = appendQuotes(history, { AAPL: q(106, AT_10_00_LATER) });
    expect(history['AAPL'].points).toEqual([100, 106]);

    history = appendQuotes(history, { AAPL: q(107, AT_10_01) });
    expect(history['AAPL']).toEqual({ points: [100, 106, 107], minute: minute(AT_10_01) });
  });

  it('caps the length', () => {
    const next = appendQuotes({ AAPL: { points: [100, 101, 102], minute: 0 } }, { AAPL: q(103) }, 3);
    expect(next['AAPL'].points).toEqual([101, 102, 103]);
  });

  it("keeps a stock's trace when its quote failed this round", () => {
    const trace: Trace = { points: [200, 201], minute: 5 };
    expect(appendQuotes({ TSLA: trace }, { TSLA: null })).toEqual({ TSLA: trace });
  });

  it('does not mutate the previous history', () => {
    const before = { AAPL: { points: [100, 101], minute: 0 } };
    appendQuotes(before, { AAPL: q(102) });
    expect(before).toEqual({ AAPL: { points: [100, 101], minute: 0 } });
  });
});

describe('seedTraces', () => {
  it("replaces traces with the server's day, its last point being this minute", () => {
    const now = Date.parse(AT_10_00);
    const seeded = seedTraces({ AAPL: { points: [1, 2], minute: 0 } }, { AAPL: [100, 101, 102] }, now);

    expect(seeded).toEqual({ AAPL: { points: [100, 101, 102], minute: minute(AT_10_00) } });
  });

  it('leaves a stock alone when the server has too little to draw a line', () => {
    expect(seedTraces({}, { AAPL: [100] }, 0)).toEqual({});
  });
});

describe('MarketHistory', () => {
  let today: Subject<Record<string, number[]>>;
  let requests: number;

  function setup(answer: () => ReturnType<PriceHistory['intraday']>) {
    requests = 0;
    TestBed.configureTestingModule({
      providers: [{ provide: PriceHistory, useValue: { intraday: () => (requests++, answer()) } }],
    });
    return TestBed.inject(MarketHistory);
  }

  it("loads today's traces once per session and carries them on with each poll", () => {
    today = new Subject();
    const history = setup(() => today);

    history.load();
    history.load();
    today.next({ AAPL: [100, 101, 102] });
    history.record({ AAPL: q(103, new Date(Date.now() + 120_000).toISOString()) });

    expect(requests).toBe(1);
    expect(history.traces()).toEqual({ AAPL: [100, 101, 102, 103] });
  });

  it('keeps one trace per stock across every page that records into it', () => {
    const fromOverview = setup(() => new Subject());
    fromOverview.record({ AAPL: q(105) });

    TestBed.inject(MarketHistory).record({ AAPL: q(106, AT_10_01) });

    expect(fromOverview.traces()).toEqual({ AAPL: [100, 105, 106] });
  });

  it('falls back to seeding from the polls when the day cannot be loaded, and retries on the next load', () => {
    const history = setup(() => throwError(() => new Error('offline')));

    history.load();
    history.record({ AAPL: q(105) });
    history.load();

    expect(history.traces()).toEqual({ AAPL: [100, 105] });
    expect(requests).toBe(2);
  });
});
