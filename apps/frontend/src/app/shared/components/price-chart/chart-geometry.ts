import { ChartPoint, ChartRange } from '../../models/chart.model';

/** A label on an axis: where it sits (0 = top/left, 1 = bottom/right) and its text. */
export interface AxisLabel {
  at: number;
  text: string;
}

/**
 * What the chart draws, in a 100 × 100 SVG box stretched over the plot area: the line, the shaded
 * area under it, the horizontal grid lines and the axis labels.
 */
export interface ChartGeometry {
  line: string;
  area: string;
  grid: string;
  yLabels: AxisLabel[];
  xLabels: AxisLabel[];
  /** Each point's position in the box, for the hover marker. */
  x: (index: number) => number;
  y: (value: number) => number;
}

const MONEY = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function money(value: number): string {
  return MONEY.format(value);
}

/**
 * A point's time as the x axis shows it: the time of day for 1D, else the date. Daily closes are
 * stamped at the exchange's closing bell, which in UTC is still the trading day, so dates are read
 * in UTC and never slip a day for a viewer far east of New York.
 */
export function axisTime(time: number, range: ChartRange): string {
  return range === '1D'
    ? new Date(time).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
    : new Date(time).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
}

/** A point's time in the hover tooltip: also names the day for 1D, and the year otherwise. */
export function fullTime(time: number, range: ChartRange): string {
  return range === '1D'
    ? `${new Date(time).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}, ${axisTime(time, range)}`
    : new Date(time).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
}

/**
 * "▲ +$12.30 (+1.25%)" for a move from `from` to `to`. The percentage is left out when the start is
 * zero, where it has no meaning.
 */
export function changeText(from: number, to: number): string {
  const change = to - from;
  const arrow = change >= 0 ? '▲ +' : '▼ −';
  const amount = money(Math.abs(change));
  if (!from) {
    return `${arrow}${amount}`;
  }
  const percent = Math.abs((change / from) * 100).toFixed(2);
  return `${arrow}${amount} (${change >= 0 ? '+' : '−'}${percent}%)`;
}

/**
 * Scales the values to the box. The y axis is three equal round steps (as on the redesign's
 * Overview board): the smallest step of 1, 2, 2.5 or 5 times a power of ten for which three steps
 * from a multiple of it below the lowest value reach the highest, so every label is a round number.
 * Points are spread evenly along x, as stock charts do, so nights and weekends leave no gaps.
 */
export function chartGeometry(points: readonly ChartPoint[], range: ChartRange): ChartGeometry | null {
  if (!points.length) {
    return null;
  }
  // A single point is drawn as a flat line across the chart.
  const series = points.length === 1 ? [points[0], points[0]] : points;
  const values = series.map((p) => p.value);
  const vmin = Math.min(...values);
  const vmax = Math.max(...values);
  // A flat series still needs a height to scale to.
  const span = vmax - vmin || Math.max(Math.abs(vmax) * 0.02, 1);
  const power = Math.pow(10, Math.floor(Math.log10(span / 3)));
  // A flat series gets room above and below, rather than sitting on the bottom or top line.
  const flat = vmax === vmin;
  const floorValue = flat ? vmin - span / 2 : vmin;
  const ceilingValue = flat ? vmax + span / 2 : vmax;
  const bottom = (m: number) => Math.floor(floorValue / m) * m;
  const nice =
    [1, 2, 2.5, 5, 10, 20, 25, 50].map((m) => m * power).find((m) => bottom(m) + m * 3 >= ceilingValue) ??
    power * 100;
  const lo = bottom(nice);
  const hi = lo + nice * 3;

  const last = series.length - 1;
  const x = (index: number) => (100 * index) / last;
  const y = (value: number) => (100 * (hi - value)) / (hi - lo);

  const line = series.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(2)} ${y(p.value).toFixed(2)}`).join(' ');
  const decimals = nice < 1 ? 2 : 0;
  const axisMoney = (v: number) =>
    hi >= 10000 && hi - lo >= 2000
      ? `$${(v / 1000).toFixed(1)}K`
      : `$${v.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}`;

  const yLabels: AxisLabel[] = [];
  const gridLines: string[] = [];
  for (let k = 0; k < 4; k++) {
    const value = lo + ((hi - lo) * k) / 3;
    const at = y(value);
    yLabels.push({ at: at / 100, text: axisMoney(value) });
    // The bottom line is the axis itself, drawn by the plot's border.
    if (k) {
      gridLines.push(`M0 ${at.toFixed(2)} H100`);
    }
  }

  // Five dates (or times) along the bottom, without repeating one when there are few points.
  const xIndexes = [...new Set([0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(f * last)))];
  const xLabels = xIndexes.map((i) => ({ at: i / last, text: axisTime(series[i].time, range) }));

  return { line, area: `${line} L100 100 L0 100 Z`, grid: gridLines.join(' '), yLabels, xLabels, x, y };
}
