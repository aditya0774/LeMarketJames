import { Type } from '@angular/core';

/** One analyst report: its URL segment under /analyst/reports, how it is named, and its page. */
export interface AnalystReport {
  readonly path: string;
  /** The page heading (and the browser tab title). */
  readonly title: string;
  /** The short name on the analyst tab bar. */
  readonly tab: string;
  /** One line under the heading saying what the report shows. */
  readonly subtitle: string;
  readonly loadComponent: () => Promise<Type<unknown>>;
}

/**
 * Every analyst report, listed once. The routes (app.routes.ts) and the analyst tab bar
 * (analyst-layout.ts) are both built from it, so a report added here gets its route, its tab and
 * its heading together.
 */
export const ANALYST_REPORTS: readonly AnalystReport[] = [
  {
    path: 'trade-activity-by-period',
    title: 'Trade activity by period',
    tab: 'By period',
    subtitle: 'Filled trades and their value, one row per period.',
    loadComponent: () =>
      import('./trade-activity-by-period/trade-activity-by-period').then((m) => m.TradeActivityByPeriod),
  },
  {
    path: 'activity-by-stock',
    title: 'Activity by stock',
    tab: 'By stock',
    subtitle: 'Trading across all clients, grouped by stock.',
    loadComponent: () => import('./activity-by-stock/activity-by-stock').then((m) => m.ActivityByStock),
  },
  {
    path: 'activity-by-client-segment',
    title: 'Activity by client segment',
    tab: 'By segment',
    subtitle: 'How Retail, Active Trader and High Net Worth clients compare.',
    loadComponent: () =>
      import('./activity-by-client-segment/activity-by-client-segment').then((m) => m.ActivityByClientSegment),
  },
  {
    path: 'overnight-reports',
    title: 'Overnight reports',
    tab: 'Overnight',
    subtitle: 'End-of-day and overnight summaries.',
    loadComponent: () => import('./overnight-reports/overnight-reports').then((m) => m.OvernightReports),
  },
];

/** The Volume tab, the analyst's start page; it is not a report under /analyst/reports. */
export const VOLUME_TAB = {
  title: 'Trading volume',
  tab: 'Volume',
  subtitle: 'How many shares changed hands, compared with the period before.',
} as const;
