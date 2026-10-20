import { ChartPoint } from '../../../shared/models/chart.model';
import { chartRangeFor, dayOf, monthsBefore, simulate } from './simulator-math';

/** A daily close stamped at 20:00 UTC, like the history endpoint's. */
const close = (day: string, value: number): ChartPoint => ({ time: Date.parse(`${day}T20:00:00Z`), value });

// 2026-05-08 is a Friday and 2026-05-11 the Monday after it.
const POINTS = [
  close('2026-05-07', 40),
  close('2026-05-08', 50),
  close('2026-05-11', 60),
  close('2026-05-12', 75),
];

describe('simulate', () => {
  it('turns dollars into fractional shares at the buy day close and values them at the latest price', () => {
    const result = simulate(POINTS, '2026-05-08', 1000)!;

    expect(result.buyDate).toBe('2026-05-08');
    expect(result.buyPrice).toBe(50);
    expect(result.shares).toBe(20);
    expect(result.currentPrice).toBe(75);
    expect(result.currentValue).toBe(1500);
    expect(result.gain).toBe(500);
    expect(result.gainPercent).toBe(50);
  });

  it('reports a loss as a negative gain', () => {
    const result = simulate([close('2026-05-07', 80), close('2026-05-08', 60)], '2026-05-07', 1000)!;

    expect(result.currentValue).toBe(750);
    expect(result.gain).toBe(-250);
    expect(result.gainPercent).toBe(-25);
  });

  it('buys fractional shares when the amount is not a multiple of the price', () => {
    expect(simulate(POINTS, '2026-05-11', 100)!.shares).toBeCloseTo(100 / 60, 10);
  });

  it('buys on the next trading day when the chosen day has no price', () => {
    const saturday = simulate(POINTS, '2026-05-09', 600)!;

    expect(saturday.buyDate).toBe('2026-05-11');
    expect(saturday.buyPrice).toBe(60);
  });

  it('uses the first price when the day is before the history starts', () => {
    expect(simulate(POINTS, '2020-01-01', 400)!.buyDate).toBe('2026-05-07');
  });

  it('uses the latest price when the day is after the history ends', () => {
    const result = simulate(POINTS, '2030-01-01', 750)!;

    expect(result.buyDate).toBe('2026-05-12');
    expect(result.gain).toBe(0);
    expect(result.daysHeld).toBe(0);
  });

  it('counts the calendar days from the purchase to the latest price', () => {
    expect(simulate(POINTS, '2026-05-07', 100)!.daysHeld).toBe(5);
  });

  it('builds the chart series from the purchase on, valued at each close', () => {
    const { series } = simulate(POINTS, '2026-05-11', 600)!;

    expect(series).toEqual([
      { time: POINTS[2].time, value: 600 },
      { time: POINTS[3].time, value: 750 },
    ]);
  });

  it('returns null when there are no prices', () => {
    expect(simulate([], '2026-05-08', 1000)).toBeNull();
  });

  it('returns null for an amount that is not a positive number', () => {
    for (const amount of [0, -5, NaN, Infinity]) {
      expect(simulate(POINTS, '2026-05-08', amount), `amount ${amount}`).toBeNull();
    }
  });

  it('returns null when the buy price is not positive', () => {
    expect(simulate([close('2026-05-08', 0), close('2026-05-09', 5)], '2026-05-08', 100)).toBeNull();
  });
});

describe('dayOf', () => {
  it('reads the UTC date of a closing-bell instant', () => {
    expect(dayOf(Date.parse('2026-05-08T20:00:00Z'))).toBe('2026-05-08');
    expect(dayOf(Date.parse('2026-01-08T21:00:00Z'))).toBe('2026-01-08');
  });
});

describe('chartRangeFor', () => {
  it('picks the smallest range that covers the holding', () => {
    const cases: [number, string][] = [
      [0, '1W'],
      [7, '1W'],
      [8, '1M'],
      [31, '1M'],
      [32, '3M'],
      [92, '3M'],
      [93, '6M'],
      [400, '6M'],
    ];
    for (const [days, range] of cases) {
      expect(chartRangeFor(days), `${days} days`).toBe(range);
    }
  });
});

describe('monthsBefore', () => {
  it('steps back whole calendar months', () => {
    expect(monthsBefore('2026-10-19', 1)).toBe('2026-09-19');
    expect(monthsBefore('2026-10-19', 6)).toBe('2026-04-19');
  });

  it('crosses a year boundary', () => {
    expect(monthsBefore('2026-02-10', 3)).toBe('2025-11-10');
  });

  it('uses the last day of a shorter month instead of spilling into the next', () => {
    expect(monthsBefore('2026-03-31', 1)).toBe('2026-02-28');
    expect(monthsBefore('2028-03-31', 1)).toBe('2028-02-29');
    expect(monthsBefore('2026-10-31', 1)).toBe('2026-09-30');
  });
});
