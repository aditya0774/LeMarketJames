import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';

import { TradeReportService } from '../../../../shared/services/trade-report.service';
import { PeriodAggregation } from '../../../../shared/models/trade-report.model';
import { formatPeriod } from '../../../../shared/utils/period-formatter';

export type PeriodType = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

/** The selectable period types, in the order they appear. */
export const PERIOD_TYPES: readonly { value: PeriodType; label: string }[] = [
  { value: 'DAY', label: 'Day' },
  { value: 'WEEK', label: 'Week' },
  { value: 'MONTH', label: 'Month' },
  { value: 'YEAR', label: 'Year' },
];

/** The look-back, in days, used for the default date range of each period type. */
const DEFAULT_LOOKBACK_DAYS: Record<PeriodType, number> = {
  DAY: 30,
  WEEK: 90,
  MONTH: 365,
  YEAR: 365 * 5,
};

/**
 * Analyst report: trade counts and values per period (C6, GET /api/v1/reports/trades), laid out
 * like the "By period" tab of the redesign's Analyst board: a period-type switch, the date range,
 * then one row per period with a bar for its value.
 * The analyst picks a period type and a date range; the table shows one row per period.
 * Fetching is delegated to TradeReportService; this component only owns the selection state.
 */
@Component({
  selector: 'staff-trade-activity-by-period',
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    CurrencyPipe,
    MatDatepickerModule,
    MatNativeDateModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
  ],
  templateUrl: './trade-activity-by-period.html',
  styleUrls: ['./trade-activity-by-period.css'],
})
export class TradeActivityByPeriod {
  private readonly reports = inject(TradeReportService);

  readonly periodTypes = PERIOD_TYPES;
  readonly columns = ['period', 'tradeCount', 'buyCount', 'sellCount', 'totalValue', 'bar'];

  readonly periodType = signal<PeriodType>('MONTH');
  readonly rows = signal<PeriodAggregation[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** True once a request has finished, so "no trades" is not shown before the first answer. */
  readonly loaded = signal(false);

  readonly totalTrades = computed(() => this.rows().reduce((sum, r) => sum + r.tradeCount, 0));
  readonly totalValue = computed(() => this.rows().reduce((sum, r) => sum + r.totalValue, 0));
  private readonly maxValue = computed(() => Math.max(0, ...this.rows().map((r) => r.totalValue)));

  readonly range = new FormGroup({
    from: new FormControl<Date | null>(null),
    to: new FormControl<Date | null>(null),
  });

  constructor() {
    this.applyDefaultRange('MONTH');
    this.load();
  }

  selectPeriodType(type: PeriodType): void {
    this.periodType.set(type);
    this.applyDefaultRange(type);
    this.load();
  }

  /** Called by the Apply button. */
  applyRange(): void {
    const { from, to } = this.range.value;
    if (!from || !to) {
      this.error.set('Choose both a start and an end date.');
      return;
    }
    if (from > to) {
      this.error.set('The start date must not be after the end date.');
      return;
    }
    this.load();
  }

  /** A period's value as a share of the biggest period, for its bar (0–100). */
  barWidth(value: number): number {
    return this.maxValue() ? (value / this.maxValue()) * 100 : 0;
  }

  label(period: string): string {
    return formatPeriod(period, this.periodType());
  }

  private applyDefaultRange(type: PeriodType): void {
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - DEFAULT_LOOKBACK_DAYS[type]);
    this.range.setValue({ from, to });
  }

  private load(): void {
    const { from, to } = this.range.value;
    if (!from || !to) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.reports.getTradeReport(this.periodType(), toIsoDay(from), toIsoDay(to)).subscribe({
      next: (response) => {
        this.rows.set(response.data);
        this.loading.set(false);
        this.loaded.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.rows.set([]);
        this.loading.set(false);
        this.loaded.set(true);
        this.error.set(messageFor(err));
      },
    });
  }
}

/** YYYY-MM-DD of the local calendar day the analyst picked (UTC could shift the day). */
function toIsoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function messageFor(err: HttpErrorResponse): string {
  switch (err.status) {
    case 400:
      return 'The period type or date range was not accepted.';
    case 401:
      return 'Your session has expired. Please sign in again.';
    case 403:
      return 'Your role cannot read reports.';
    default:
      return 'The report could not be loaded. Try again.';
  }
}
