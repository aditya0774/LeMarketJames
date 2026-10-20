import { ActiveClientDay } from '../models/active-client-trend.model';

/** Drawing area of the chart, in SVG units; the SVG scales to its container. */
export const CHART_WIDTH = 600;
export const CHART_HEIGHT = 160;

/** One day drawn as a bar. */
export interface TrendBar {
  date: string;
  activeClients: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface TrendChart {
  bars: TrendBar[];
  /** Top of the shared zero-based scale; 0 when no day had a trade. */
  maximum: number;
}

/**
 * Lays the days out as bars on one zero-based scale, oldest on the left. A window without
 * any trade gets a maximum of 0 and zero-height bars rather than a division by zero.
 */
export function buildTrendChart(days: readonly ActiveClientDay[]): TrendChart {
  const maximum = days.reduce((max, d) => Math.max(max, d.activeClients), 0);
  const slot = days.length > 0 ? CHART_WIDTH / days.length : 0;
  const bars = days.map((d, i) => {
    const height = maximum > 0 ? (d.activeClients / maximum) * CHART_HEIGHT : 0;
    return {
      date: d.date,
      activeClients: d.activeClients,
      x: i * slot + slot * 0.1,
      y: CHART_HEIGHT - height,
      width: slot * 0.8,
      height,
    };
  });
  return { bars, maximum };
}
