import { buildTrendChart, CHART_HEIGHT, CHART_WIDTH } from './trend-chart';

const days = (...counts: number[]) =>
  counts.map((activeClients, i) => ({ date: `2026-10-${String(i + 1).padStart(2, '0')}`, activeClients }));

describe('buildTrendChart', () => {
  it('scales every bar against the peak, with the peak at full height', () => {
    const { bars, maximum } = buildTrendChart(days(5, 10, 0));

    expect(maximum).toBe(10);
    expect(bars.map((b) => b.height)).toEqual([CHART_HEIGHT / 2, CHART_HEIGHT, 0]);
    expect(bars.every((b) => b.y + b.height === CHART_HEIGHT)).toBe(true);
  });

  it('keeps the days in order, left to right, inside the drawing area', () => {
    const { bars } = buildTrendChart(days(1, 2, 3, 4));

    expect(bars.map((b) => b.date)).toEqual(['2026-10-01', '2026-10-02', '2026-10-03', '2026-10-04']);
    expect(bars.map((b) => b.x)).toEqual([...bars.map((b) => b.x)].sort((a, b) => a - b));
    expect(bars[0].x).toBeGreaterThanOrEqual(0);
    expect(bars[3].x + bars[3].width).toBeLessThanOrEqual(CHART_WIDTH);
  });

  it('gives a window without trades a maximum of 0 and flat bars, not NaN', () => {
    const { bars, maximum } = buildTrendChart(days(0, 0));

    expect(maximum).toBe(0);
    expect(bars.every((b) => b.height === 0 && Number.isFinite(b.y))).toBe(true);
  });

  it('handles no days at all', () => {
    expect(buildTrendChart([])).toEqual({ bars: [], maximum: 0 });
  });
});
