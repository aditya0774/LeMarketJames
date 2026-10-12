import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ANALYST_REPORTS } from './reports/analyst-reports';

/** Report metadata for dashboard display */
interface ReportMeta {
  title: string;
  description: string;
  icon: string;
  path: string;
}

/**
 * Landing page of the ANALYST role: displays app-wide aggregate reports in a professional card grid.
 * Analysts never see individual clients, so nothing here or in reports may identify one.
 * Reports: Period aggregates, stock activity, client segments, overnight summaries.
 */
@Component({
  selector: 'staff-analyst-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './analyst-dashboard.html',
  styleUrls: ['./analyst-dashboard.scss'],
})
export class AnalystDashboard {
  protected readonly reports = ANALYST_REPORTS;

  /** Report metadata for enhanced dashboard display */
  protected readonly reportMeta: Record<string, ReportMeta> = {
    'trade-activity-by-period': {
      title: 'Trade Activity by Period',
      description: 'Aggregate trade counts and values by time period (day, week, month, or year).',
      icon: 'calendar_today',
      path: 'trade-activity-by-period',
    },
    'activity-by-stock': {
      title: 'Activity by Stock',
      description: 'Analyze trading volume and gross amounts aggregated by stock symbol.',
      icon: 'trending_up',
      path: 'activity-by-stock',
    },
    'activity-by-client-segment': {
      title: 'Activity by Client Segment',
      description: 'Segment trading activity by client type and investment profile.',
      icon: 'people',
      path: 'activity-by-client-segment',
    },
    'overnight-reports': {
      title: 'Overnight Reports',
      description: 'End-of-day and overnight trading summary reports.',
      icon: 'nights_stay',
      path: 'overnight-reports',
    },
  };

  /**
   * Get metadata for a report by path
   */
  getReportMeta(path: string): ReportMeta | undefined {
    return this.reportMeta[path];
  }
}
