import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl } from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';

import { ReportingService, TradesByStockRow } from '../../../core/reports/reporting.service';
import { ByStockAggregateTable } from './by-stock-aggregate-table';

/**
 * Activity By Stock Report Container Component (LMKT-42 for staff app)
 *
 * Displays aggregate trading activity by stock symbol for ANALYST users.
 * Includes date range filtering (start/end date pickers).
 * Default date range: last 30 days.
 * Handles loading, error, and empty states.
 */
@Component({
  selector: 'staff-activity-by-stock',
  templateUrl: './activity-by-stock.component.html',
  styleUrls: ['./activity-by-stock.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatInputModule,
    MatFormFieldModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatCardModule,
    ByStockAggregateTable,
  ]
})
export class ActivityByStock implements OnInit {
  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tradesData = signal<readonly TradesByStockRow[]>([]);

  // Form for date range selection
  dateRangeForm = new FormGroup({
    startDate: new FormControl<Date | null>(null),
    endDate: new FormControl<Date | null>(null),
  });

  constructor(private reportingService: ReportingService) {}

  ngOnInit(): void {
    // Set default date range: last 30 days
    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);

    this.dateRangeForm.patchValue({
      startDate: thirtyDaysAgo,
      endDate: today,
    });

    // Load initial data
    this.loadReport();
  }

  /**
   * Load report data for the current date range
   */
  protected loadReport(): void {
    this.error.set(null);
    this.isLoading.set(true);

    const startDate = this.dateRangeForm.get('startDate')?.value;
    const endDate = this.dateRangeForm.get('endDate')?.value;

    const startDateStr = startDate ? this.formatDate(startDate) : undefined;
    const endDateStr = endDate ? this.formatDate(endDate) : undefined;

    this.reportingService.getTradesByStock(startDateStr, endDateStr).subscribe({
      next: (response) => {
        if (response.success) {
          this.tradesData.set(response.data);
          this.error.set(null);
        } else {
          this.error.set('Unable to load report. Please try again.');
        }
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        // Handle specific HTTP errors
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
      }
    });
  }

  /**
   * Handle date range change and reload report
   */
  protected onDateRangeChange(): void {
    this.loadReport();
  }

  /**
   * Format Date to YYYY-MM-DD string for API
   */
  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /**
   * Retry loading the report
   */
  protected onRetry(): void {
    this.loadReport();
  }
}
