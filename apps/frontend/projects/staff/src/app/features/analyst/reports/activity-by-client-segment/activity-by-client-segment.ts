import { SegmentComparisonCharts } from './segment-comparison-charts';
import { Component, OnInit, OnDestroy, computed, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import {
  ReportingService,
  SegmentActivityRow,
  SegmentComparisonResponse,
} from '@staff/core/reports/reporting.service';

/** Whether both ends of a date range are set and the start is on or before the end. */
function isOrderedRange(start: Date | null, end: Date | null): boolean {
  return !!start && !!end && start <= end;
}

/**
 * Activity By Client Segment report (LMKT-43).
 *
 * Shows aggregate activity per segment for the selected date range.
 */
@Component({
  selector: 'staff-activity-by-client-segment',
  templateUrl: './activity-by-client-segment.html',
  styleUrls: ['./activity-by-client-segment.scss'],
  standalone: true,
  imports: [
    SegmentComparisonCharts,
    CommonModule,
    ReactiveFormsModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatInputModule,
    MatSelectModule,
    MatFormFieldModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatCardModule,
  ],
})
export class ActivityByClientSegment implements OnInit, OnDestroy {
  protected readonly comparison = signal<SegmentComparisonResponse | null>(null);
  private request?: Subscription;
  ngOnDestroy(): void { this.request?.unsubscribe(); }
  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly reportData = signal<readonly SegmentActivityRow[]>([]);
  protected readonly allTimeData = signal<readonly SegmentActivityRow[]>([]);
  protected readonly chartRows = computed(() =>
    [...this.allTimeData()].sort((a, b) => b.totalTradedValue - a.totalTradedValue)
  );
  protected readonly totalTradedValue = computed(() =>
    this.allTimeData().reduce((sum, row) => sum + row.totalTradedValue, 0)
  );
  protected readonly totalTrades = computed(() =>
    this.allTimeData().reduce((sum, row) => sum + row.tradeCount, 0)
  );
  protected readonly totalActiveClients = computed(() =>
    this.allTimeData().reduce((sum, row) => sum + row.activeClients, 0)
  );

  dateRangeForm = new FormGroup({
    startDate: new FormControl<Date | null>(null),
    endDate: new FormControl<Date | null>(null),
    comparisonMode: new FormControl<'previous' | 'custom'>('previous', { nonNullable: true }),
    comparisonStartDate: new FormControl<Date | null>({ value: null, disabled: true }),
    comparisonEndDate: new FormControl<Date | null>({ value: null, disabled: true }),
  });

  constructor(private readonly reportingService: ReportingService) {}

  ngOnInit(): void {
    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);

    this.dateRangeForm.patchValue({
      startDate: thirtyDaysAgo,
      endDate: today,
    });

    this.loadReport();
  }

  protected onDateRangeChange(): void {
    this.loadReport();
  }

  protected onComparisonModeChange(): void {
    const { comparisonMode, comparisonStartDate, comparisonEndDate } = this.dateRangeForm.controls;
    // Hidden custom fields must not invalidate or override the automatic comparison.
    for (const control of [comparisonStartDate, comparisonEndDate]) {
      if (comparisonMode.value === 'custom') {
        control.enable();
      } else {
        control.disable();
      }
    }
    this.error.set(null);
    if (comparisonMode.value === 'previous') {
      this.loadReport();
    }
  }

  protected onRetry(): void {
    this.loadReport();
  }

  protected barWidthPercent(value: number): number {
    // Match the bar length to the share-of-total label beside it.
    const total = this.totalTradedValue();
    if (total <= 0) {
      return 0;
    }
    return (value / total) * 100;
  }

  protected selectedRangeLabel(): string {
    const startDate = this.dateRangeForm.get('startDate')?.value;
    const endDate = this.dateRangeForm.get('endDate')?.value;
    if (!startDate || !endDate) {
      return 'Custom date range';
    }
    return `${this.formatDate(startDate)} to ${this.formatDate(endDate)}`;
  }

  private loadReport(): void {
    this.request?.unsubscribe();
    this.error.set(null);
    this.comparison.set(null);
    const values = this.dateRangeForm.getRawValue();
    const custom = values.comparisonMode === 'custom';
    const comparisonValid = !custom || isOrderedRange(values.comparisonStartDate, values.comparisonEndDate);
    if (this.dateRangeForm.invalid || !isOrderedRange(values.startDate, values.endDate) || !comparisonValid) {
      this.isLoading.set(false);
      this.error.set(custom
        ? 'Invalid date range. Enter both comparison dates with the start on or before the end.'
        : 'Invalid date range. Check the selected start and end dates.');
      return;
    }
    this.isLoading.set(true);

    const startDate = this.dateRangeForm.get('startDate')?.value;
    const endDate = this.dateRangeForm.get('endDate')?.value;

    const startDateStr = startDate ? this.formatDate(startDate) : undefined;
    const endDateStr = endDate ? this.formatDate(endDate) : undefined;

    this.request = this.reportingService.getSegmentComparison(startDateStr, endDateStr,
      custom && values.comparisonStartDate ? this.formatDate(values.comparisonStartDate) : undefined,
      custom && values.comparisonEndDate ? this.formatDate(values.comparisonEndDate) : undefined).subscribe({
      next: (response) => {
        if (response.success) {
          this.reportData.set(response.data);
          this.allTimeData.set(response.allTimeData ?? []);
          this.comparison.set(response);
          this.error.set(null);
        } else {
          this.error.set('Unable to load report. Please try again.');
        }
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 403) {
          this.error.set('You do not have permission to access this report.');
        } else if (err.status === 401) {
          this.error.set('Your session has expired. Please log in again.');
        } else if (err.status === 400) {
          this.error.set('Invalid date range. Please check your selection.');
        } else {
          this.error.set('Unable to load report. Please try again.');
        }
        console.error('Report loading error:', err);
      },
    });
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
