import { Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SegmentComparisonRow, SegmentComparisonResponse } from '@staff/core/reports/reporting.service';

type Metric = 'activeClients' | 'tradeCount' | 'totalTradedValue';

/** Each metric has one zero-based scale shared by all segments and both periods. */
@Component({
  selector: 'staff-segment-comparison-charts',
  imports: [CommonModule, MatTooltipModule],
  templateUrl: './segment-comparison-charts.html',
  styleUrl: './segment-comparison-charts.scss',
})
export class SegmentComparisonCharts {
  readonly rows = input.required<readonly SegmentComparisonRow[]>();
  readonly dates = input.required<Pick<SegmentComparisonResponse,
    'startDate' | 'endDate' | 'comparisonStartDate' | 'comparisonEndDate'>>();
  // Use the returned report dates so labels always describe the displayed data.
  protected readonly periods = computed(() => [
    { key: 'previous', label: `${this.dates().comparisonStartDate} to ${this.dates().comparisonEndDate}` },
    { key: 'current', label: `${this.dates().startDate} to ${this.dates().endDate}` },
  ] as const);
  protected readonly charts = computed(() => [
    { key: 'activeClients' as const, label: 'Active clients' },
    { key: 'tradeCount' as const, label: 'Filled trades' },
    { key: 'totalTradedValue' as const, label: 'Traded value (USD)' },
  ].map(metric => ({ ...metric,
    maximum: this.rows().reduce((max, row) => Math.max(max, row.current[metric.key], row.previous[metric.key]), 0),
  })));

  protected width(value: number, maximum: number): number {
    return maximum > 0 ? value / maximum * 100 : 0;
  }

  protected format(value: number, metric: Metric): string {
    return new Intl.NumberFormat('en-US', metric === 'totalTradedValue'
      ? { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 }
      : { maximumFractionDigits: 0 }).format(value);
  }

  protected change(percent: number | null): string {
    return percent === null ? 'N/A' : `${percent > 0 ? '+' : ''}${percent.toFixed(2)}%`;
  }
}
