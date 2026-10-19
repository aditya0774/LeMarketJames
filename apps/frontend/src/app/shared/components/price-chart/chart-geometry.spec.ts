import { axisTime, changeText, chartGeometry, fullTime } from './chart-geometry';

const DAY = 86_400_000;
// 20:00 UTC: a New York close, still the same date in UTC.
const OCT_5 = Date.parse('2026-10-05T20:00:00Z');

describe('chartGeometry', () => {
  it('draws nothing without points', () => {
    expect(chartGeometry([], '1M')).toBeNull();
  });

  it('spreads points evenly and puts the y axis on round numbers containing them', () => {
    const g = chartGeometry(
      [
        { time: OCT_5, value: 1020 },
        { time: OCT_5 + DAY, value: 1100 },
        { time: OCT_5 + 2 * DAY, value: 1060 },
      ],
      '1M',
    )!;

    // 1020–1100: steps of 25 from 1000 stop short at 1075, so the axis runs 1000–1150 in steps of 50.
    expect(g.yLabels.map((l) => l.text)).toEqual(['$1,000', '$1,050', '$1,100', '$1,150']);
    expect(g.line).toBe('M0.00 86.67 L50.00 33.33 L100.00 60.00');
    expect(g.area).toBe(`${g.line} L100 100 L0 100 Z`);
    // Three grid lines; the bottom one is the axis.
    expect(g.grid.split('M').filter(Boolean)).toHaveLength(3);
  });

  it('labels the x axis with dates, without repeating one', () => {
    const g = chartGeometry(
      [
        { time: OCT_5, value: 10 },
        { time: OCT_5 + DAY, value: 11 },
      ],
      '1W',
    )!;

    expect(g.xLabels).toEqual([
      { at: 0, text: 'Oct 5' },
      { at: 1, text: 'Oct 6' },
    ]);
  });

  it('shows cents on a narrow axis and thousands on a wide one', () => {
    const narrow = chartGeometry([{ time: 1, value: 12.1 }, { time: 2, value: 12.4 }], '1D')!;
    expect(narrow.yLabels[0].text).toMatch(/^\$12\.\d\d$/);

    const wide = chartGeometry([{ time: 1, value: 11_620 }, { time: 2, value: 16_186 }], '3M')!;
    expect(wide.yLabels.map((l) => l.text)).toEqual(['$10.0K', '$12.5K', '$15.0K', '$17.5K']);
  });

  it('draws a single point as a flat line through the middle of its axis', () => {
    const g = chartGeometry([{ time: OCT_5, value: 250 }], '1D')!;

    expect(g.line).toMatch(/^M0\.00 (\S+) L100\.00 \1$/);
    const y = Number(g.line.split(' ')[1]);
    expect(y).toBeGreaterThan(0);
    expect(y).toBeLessThan(100);
  });
});

describe('chart text', () => {
  it('describes a change with its percentage, and without one from zero', () => {
    expect(changeText(100, 112.3)).toBe('▲ +$12.30 (+12.30%)');
    expect(changeText(100, 95)).toBe('▼ −$5.00 (−5.00%)');
    expect(changeText(0, 50)).toBe('▲ +$50.00');
  });

  it('reads daily closes as UTC dates and 1D as times of day', () => {
    expect(axisTime(OCT_5, '6M')).toBe('Oct 5');
    expect(fullTime(OCT_5, '6M')).toBe('Oct 5, 2026');
    expect(axisTime(OCT_5, '1D')).toMatch(/^\d{1,2}:\d\d (AM|PM)$/);
  });
});
