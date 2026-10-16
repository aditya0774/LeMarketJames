import { allocation, portfolioTotals } from './portfolio-totals';
import { HoldingDto } from '../../shared/models/holdings.model';

const holding = (symbol: string, currentValue: number, totalCost: number): HoldingDto => ({
  symbol, quantity: 1, averageCost: totalCost, currentPrice: currentValue, totalCost, currentValue,
  gainLoss: currentValue - totalCost, gainLossPercent: ((currentValue - totalCost) / totalCost) * 100,
});

describe('portfolio totals', () => {
  it('sums value, cost and gain', () => {
    expect(portfolioTotals([holding('A', 1200, 1000), holding('B', 400, 500)])).toEqual({
      value: 1600, cost: 1500, gainLoss: 100, gainLossPercent: (100 / 1500) * 100,
    });
  });

  it('is all zero with nothing held', () => {
    expect(portfolioTotals([])).toEqual({ value: 0, cost: 0, gainLoss: 0, gainLossPercent: 0 });
  });

  it('splits the value largest first', () => {
    const slices = allocation([holding('B', 250, 250), holding('A', 750, 500)]);

    expect(slices.map((s) => [s.symbol, s.percent])).toEqual([['A', 75], ['B', 25]]);
    expect(slices[0].gainLossPercent).toBe(50);
  });

  it('has no slices for an empty portfolio', () => {
    expect(allocation([])).toEqual([]);
  });
});
