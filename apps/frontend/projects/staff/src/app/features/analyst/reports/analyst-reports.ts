import { Type } from '@angular/core';

/** One analyst report: its URL segment under /analyst/reports, its title, and its page. */
export interface AnalystReport {
  readonly path: string;
  readonly title: string;
  readonly loadComponent: () => Promise<Type<unknown>>;
}

/**
 * Every analyst report, listed once. The routes (app.routes.ts) and the dashboard's "Reports"
 * list are both built from it, so a report added here gets its route and its link together.
 */
export const ANALYST_REPORTS: readonly AnalystReport[] = [
  {
    path: 'trade-activity-by-period',
    title: 'Trade activity by period',
    loadComponent: () =>
      import('./trade-activity-by-period/trade-activity-by-period').then((m) => m.TradeActivityByPeriod),
  },
  {
    path: 'activity-by-stock',
    title: 'Activity by stock',
    loadComponent: () => import('./activity-by-stock/activity-by-stock').then((m) => m.ActivityByStock),
  },
  {
    path: 'activity-by-client-segment',
    title: 'Activity by client segment',
    loadComponent: () =>
      import('./activity-by-client-segment/activity-by-client-segment').then((m) => m.ActivityByClientSegment),
  },
  {
    path: 'overnight-reports',
    title: 'Overnight reports',
    loadComponent: () => import('./overnight-reports/overnight-reports').then((m) => m.OvernightReports),
  },
];
