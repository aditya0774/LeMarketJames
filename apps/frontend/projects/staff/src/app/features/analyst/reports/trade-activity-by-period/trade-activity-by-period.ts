import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl } from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ReportingService, TradesByStockRow, TradesReportResponse } from '../../../../core/reports/reporting.service';

type PeriodType = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

/**
 * Analyst report: Trade activity by period.
 * Displays aggregate trade counts and values by selected period type (day/week/month/year)
 * and date range. Data sourced from /api/v1/reports/trades-by-stock (ANALYST-only endpoint).
 */
@Component({
  selector: 'staff-trade-activity-by-period',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatInputModule,
    MatFormFieldModule,
    MatTableModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatCardModule,
    MatTabsModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './trade-activity-by-period.html',
  styleUrls: ['./trade-activity-by-period.css'],
})
export class TradeActivityByPeriod implements OnInit {
  private readonly reportingService = inject(ReportingService);

  // State signals
  readonly periodType = signal<PeriodType>('MONTH');
  readonly startDate = signal<Date | null>(null);
  readonly endDate = signal<Date | null>(null);
  readonly reportData = signal<TradesByStockRow[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  // Table columns to display
  readonly displayedColumns: string[] = [
    'symbol',
    'totalQuantity',
    'totalGrossAmount',
    'buyCount',
    'sellCount',
  ];

  // Computed: derived data for display
  readonly hasData = computed(() => this.reportData().length > 0);
  readonly showEmpty = computed(() => !this.isLoading() && !this.hasData() && !this.error());
  readonly totalTradeCount = computed(() =>
    this.reportData().reduce((sum, row) => sum + (row.buyCount + row.sellCount), 0)
  );
  readonly totalQuantity = computed(() =>
    this.reportData().reduce((sum, row) => sum + row.totalQuantity, 0)
  );
  readonly totalGrossAmount = computed(() =>
    this.reportData().reduce((sum, row) => sum + row.totalGrossAmount, 0)
  );

  // Form group for date range
  readonly dateRangeForm = new FormGroup({
    startDate: new FormControl<Date | null>(null),
    endDate: new FormControl<Date | null>(null),
  });

  ngOnInit(): void {
    // Initialize with default date range (last 30 days)
    const today = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 30);

    this.startDate.set(thirtyDaysAgo);
    this.endDate.set(today);

    this.dateRangeForm.patchValue({
      startDate: thirtyDaysAgo,
      endDate: today,
    });

    // Load initial report
    this.loadReport();
  }

  /**
   * Handle period type change (DAY/WEEK/MONTH/YEAR).
   * Adjusts the default date range and reloads the report.
   */
  onPeriodTypeChange(newPeriod: PeriodType): void {
    this.periodType.set(newPeriod);
    this.adjustDateRangeForPeriod(newPeriod);
    this.loadReport();
  }

  /**
   * Handle date range change.
   * Validates the range and reloads the report if valid.
   */
  onDateRangeChange(): void {
    const start = this.dateRangeForm.get('startDate')?.value;
    const end = this.dateRangeForm.get('endDate')?.value;

    if (!start || !end) {
      this.error.set('Both start and end dates are required.');
      return;
    }

    if (start > end) {
      this.error.set('Start date must be before or equal to end date.');
      return;
    }

    this.startDate.set(start);
    this.endDate.set(end);
    this.error.set(null);
    this.loadReport();
  }

  /**
   * Manually refresh the report with current date range.
   */
  refresh(): void {
    this.loadReport();
  }

  /**
   * Set a preset date range (last 7 days, this month, this quarter).
   * Provides quick date selection for common reporting periods.
   */
  setPresetDates(preset: 'week' | 'month' | 'quarter'): void {
    const today = new Date();
    const startDate = new Date();

    switch (preset) {
      case 'week':
        // Last 7 days
        startDate.setDate(today.getDate() - 7);
        break;
      case 'month':
        // First day of current month
        startDate.setMonth(today.getMonth());
        startDate.setDate(1);
        break;
      case 'quarter':
        // First day of current quarter (Q1 Jan-Mar, Q2 Apr-Jun, Q3 Jul-Sep, Q4 Oct-Dec)
        const quarter = Math.floor(today.getMonth() / 3);
        startDate.setMonth(quarter * 3);
        startDate.setDate(1);
        break;
    }

    this.startDate.set(startDate);
    this.endDate.set(today);
    this.dateRangeForm.patchValue({
      startDate: startDate,
      endDate: today,
    });
    this.loadReport();
  }

  /**
   * Private: Adjust date range based on period type.
   * For all types, keep the current end date and compute start based on period.
   */
  private adjustDateRangeForPeriod(period: PeriodType): void {
    const today = new Date();
    let start = new Date();

    switch (period) {
      case 'DAY':
        // Last 1 day (today only)
        start = new Date(today);
        break;
      case 'WEEK':
        // Last 7 days
        start = new Date();
        start.setDate(today.getDate() - 7);
        break;
      case 'MONTH':
        // Last 30 days
        start = new Date();
        start.setDate(today.getDate() - 30);
        break;
      case 'YEAR':
        // Last 365 days
        start = new Date();
        start.setDate(today.getDate() - 365);
        break;
    }

    this.startDate.set(start);
    this.endDate.set(today);

    this.dateRangeForm.patchValue({
      startDate: start,
      endDate: today,
    });
  }

  /**
   * Private: Load the report from the API.
   * Calls ReportingService.getTradesByStock() with the current date range.
   */
  private loadReport(): void {
    const start = this.startDate();
    const end = this.endDate();

    if (!start || !end) {
      this.error.set('Invalid date range.');
      return;
    }

    this.isLoading.set(true);
    this.error.set(null);
    this.reportData.set([]);

    const startDateStr = this.formatDateForApi(start);
    const endDateStr = this.formatDateForApi(end);

    this.reportingService.getTradesByStock(startDateStr, endDateStr).subscribe({
      next: (response: TradesReportResponse) => {
        if (response.success) {
          this.reportData.set(response.data);
        } else {
          this.error.set('Failed to load report data.');
        }
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.handleApiError(err);
      },
    });
  }

  /**
   * Handle API errors with appropriate messages.
   */
  private handleApiError(error: any): void {
    const status = error.status || 0;

    if (status === 401) {
      this.error.set('Your session has expired. Please log in again.');
    } else if (status === 403) {
      this.error.set('You do not have permission to access reports.');
    } else if (status === 400) {
      this.error.set('Invalid date range or request format.');
    } else {
      this.error.set(`An error occurred: ${error.message || 'Unknown error'}`);
    }
  }

  /**
   * Format a Date object to YYYY-MM-DD string for API calls.
   */
  private formatDateForApi(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /**
   * Format a number as USD currency for display.
   */
  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }

  /**
   * Format a Date object for display.
   */
  formatDateForDisplay(date: Date): string {
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(date);
  }

  /**
   * Get a hint/tooltip for each period type to help users understand the aggregation level.
   */
  getPeriodTypeHint(period: PeriodType): string {
    const hints: Record<PeriodType, string> = {
      DAY: 'Aggregates trades for each day within the date range',
      WEEK: 'Aggregates trades for each week (Sunday-Saturday) within the date range',
      MONTH: 'Aggregates trades for each month within the date range',
      YEAR: 'Aggregates trades for each year within the date range',
    };
    return hints[period];
  }
}
