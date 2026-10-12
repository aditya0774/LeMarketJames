import { ByClientSegmentAggregateTable } from './by-client-segment-aggregate-table';
import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import {
  ReportingService,
  SegmentActivityRow,
} from '@staff/core/reports/reporting.service';

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
    ByClientSegmentAggregateTable,
    CommonModule,
    ReactiveFormsModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatInputModule,
    MatFormFieldModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatCardModule,
  ],
})
export class ActivityByClientSegment implements OnInit {
  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly reportData = signal<readonly SegmentActivityRow[]>([]);
  protected readonly chartRows = computed(() =>
    [...this.reportData()].sort((a, b) => b.totalTradedValue - a.totalTradedValue)
  );
  protected readonly maxTradedValue = computed(() => {
    const values = this.chartRows().map((row) => row.totalTradedValue);
    return values.length > 0 ? Math.max(...values) : 0;
  });
  protected readonly totalTradedValue = computed(() =>
    this.reportData().reduce((sum, row) => sum + row.totalTradedValue, 0)
  );
  protected readonly totalTrades = computed(() =>
    this.reportData().reduce((sum, row) => sum + row.tradeCount, 0)
  );
  protected readonly totalActiveClients = computed(() =>
    this.reportData().reduce((sum, row) => sum + row.activeClients, 0)
  );

  dateRangeForm = new FormGroup({
    startDate: new FormControl<Date | null>(null),
    endDate: new FormControl<Date | null>(null),
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

  protected onRetry(): void {
    this.loadReport();
  }

  protected barWidthPercent(value: number): number {
    const max = this.maxTradedValue();
    if (max <= 0) {
      return 0;
    }
    return (value / max) * 100;
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
    this.error.set(null);
    this.isLoading.set(true);

    const startDate = this.dateRangeForm.get('startDate')?.value;
    const endDate = this.dateRangeForm.get('endDate')?.value;

    const startDateStr = startDate ? this.formatDate(startDate) : undefined;
    const endDateStr = endDate ? this.formatDate(endDate) : undefined;

    this.reportingService.getActivityByClientSegment(startDateStr, endDateStr).subscribe({
      next: (response) => {
        if (response.success) {
          this.reportData.set(response.data);
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
