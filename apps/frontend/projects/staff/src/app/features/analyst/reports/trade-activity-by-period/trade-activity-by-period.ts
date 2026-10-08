import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TradeReportService } from '../../../../shared/services/trade-report.service';
import { PeriodAggregation } from '../../../../shared/models/trade-report.model';
import { formatPeriod } from '../../../../shared/utils/period-formatter';

/** Analyst report "Trade activity by period". Displays trades aggregated by day, week, month, or year. */
@Component({
  selector: 'staff-trade-activity-by-period',
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="trade-report-container">
      <h1>Trade activity by period</h1>

      <!-- Period Type Selector (Tabs) -->
      <div class="period-selector">
        <label>Period Type:</label>
        <div class="tab-group">
          @for (type of periodTypes; track type) {
            <button
              [class.active]="periodType === type"
              (click)="onPeriodTypeChange(type)"
              class="tab-button"
            >
              {{ type }}
            </button>
          }
        </div>
      </div>

      <!-- Date Range Inputs -->
      <div class="date-range">
        <div class="form-group">
          <label for="fromDate">From:</label>
          <input
            type="date"
            id="fromDate"
            [(ngModel)]="fromDate"
            (change)="onDateChange()"
            class="date-input"
          />
        </div>
        <div class="form-group">
          <label for="toDate">To:</label>
          <input
            type="date"
            id="toDate"
            [(ngModel)]="toDate"
            (change)="onDateChange()"
            class="date-input"
          />
        </div>
      </div>

      <!-- Loading State -->
      @if (loading) {
        <div class="loading">
          <p>Loading trade data...</p>
        </div>
      }

      <!-- Error State -->
      @if (error) {
        <div class="error-alert">
          <p><strong>Error:</strong> {{ error }}</p>
          <button (click)="loadReport()" class="retry-button">Retry</button>
        </div>
      }

      <!-- Results Table -->
      @if (!loading && !error && data.length === 0) {
        <div class="empty-state">
          <p>No trades found in this period.</p>
        </div>
      }

      @if (!loading && !error && data.length > 0) {
        <div class="table-container">
          <table class="results-table">
            <thead>
              <tr>
                <th>Period</th>
                <th class="number-column">Trade Count</th>
                <th class="number-column">Buy</th>
                <th class="number-column">Sell</th>
                <th class="number-column">Total Value</th>
              </tr>
            </thead>
            <tbody>
              @for (row of data; track row.period) {
                <tr>
                  <td>{{ formatPeriodLabel(row.period) }}</td>
                  <td class="number-column">{{ row.tradeCount }}</td>
                  <td class="number-column">{{ row.buyCount }}</td>
                  <td class="number-column">{{ row.sellCount }}</td>
                  <td class="number-column currency">
                    {{ row.totalValue | currency: 'USD':'symbol':'1.2-2' }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- Back Link -->
      <div class="back-link">
        <a routerLink="/analyst">← Back to reports</a>
      </div>
    </div>
  `,
  styles: `
    .trade-report-container {
      padding: 20px;
      max-width: 1200px;
    }

    h1 {
      margin-bottom: 20px;
      color: #333;
    }

    .period-selector {
      margin-bottom: 20px;
    }

    .period-selector label {
      display: block;
      margin-bottom: 8px;
      font-weight: 500;
    }

    .tab-group {
      display: flex;
      gap: 8px;
      margin-bottom: 20px;
    }

    .tab-button {
      padding: 8px 16px;
      border: 1px solid #ddd;
      background: #f5f5f5;
      cursor: pointer;
      border-radius: 4px;
      transition: all 0.2s;
    }

    .tab-button:hover {
      background: #e8e8e8;
    }

    .tab-button.active {
      background: #007bff;
      color: white;
      border-color: #007bff;
    }

    .date-range {
      display: flex;
      gap: 20px;
      margin-bottom: 20px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    .form-group label {
      margin-bottom: 4px;
      font-weight: 500;
    }

    .date-input {
      padding: 8px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 14px;
    }

    .loading {
      padding: 40px;
      text-align: center;
      color: #666;
      font-style: italic;
    }

    .error-alert {
      padding: 16px;
      margin-bottom: 20px;
      background: #f8d7da;
      border: 1px solid #f5c6cb;
      border-radius: 4px;
      color: #721c24;
    }

    .error-alert p {
      margin: 0 0 10px 0;
    }

    .retry-button {
      padding: 6px 12px;
      background: #721c24;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }

    .retry-button:hover {
      background: #5a151b;
    }

    .empty-state {
      padding: 40px;
      text-align: center;
      color: #666;
    }

    .table-container {
      margin-bottom: 20px;
      overflow-x: auto;
    }

    .results-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }

    .results-table thead {
      background: #f5f5f5;
      border-bottom: 2px solid #ddd;
    }

    .results-table th,
    .results-table td {
      padding: 12px;
      text-align: left;
    }

    .results-table th {
      font-weight: 600;
      color: #333;
    }

    .results-table tbody tr:hover {
      background: #f9f9f9;
    }

    .results-table tbody tr:nth-child(even) {
      background: #fafafa;
    }

    .number-column {
      text-align: right;
      font-family: monospace;
    }

    .currency {
      font-weight: 500;
      color: #007bff;
    }

    .back-link {
      margin-top: 20px;
      padding-top: 20px;
      border-top: 1px solid #ddd;
    }

    .back-link a {
      color: #007bff;
      text-decoration: none;
    }

    .back-link a:hover {
      text-decoration: underline;
    }
  `,
})
export class TradeActivityByPeriod implements OnInit {
  periodTypes = ['DAY', 'WEEK', 'MONTH', 'YEAR'];
  periodType = 'DAY';
  fromDate = '';
  toDate = '';
  data: PeriodAggregation[] = [];
  loading = false;
  error: string | null = null;

  constructor(private tradeReportService: TradeReportService) {
    // Initialize dates to last 7 days
    this.initializeDateRange();
  }

  ngOnInit(): void {
    this.loadReport();
  }

  /**
   * Initialize date range to last 7 days.
   */
  private initializeDateRange(): void {
    const today = new Date();
    const sevenDaysAgo = new Date(today);
    sevenDaysAgo.setDate(today.getDate() - 7);

    this.toDate = this.formatDateForInput(today);
    this.fromDate = this.formatDateForInput(sevenDaysAgo);
  }

  /**
   * Format a Date object to YYYY-MM-DD string for input element.
   */
  private formatDateForInput(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /**
   * Handle period type tab change.
   */
  onPeriodTypeChange(type: string): void {
    this.periodType = type;
    this.loadReport();
  }

  /**
   * Handle date range change.
   */
  onDateChange(): void {
    this.loadReport();
  }

  /**
   * Load trade report from service.
   */
  loadReport(): void {
    this.loading = true;
    this.error = null;
    this.data = [];

    this.tradeReportService
      .getTradeReport(this.periodType, this.fromDate, this.toDate)
      .subscribe({
        next: (response) => {
          this.data = response.data;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = this.getErrorMessage(err);
        },
      });
  }

  /**
   * Format a period key to human-readable label.
   */
  formatPeriodLabel(period: string): string {
    return formatPeriod(period, this.periodType);
  }

  /**
   * Extract user-friendly error message from HTTP error.
   */
  private getErrorMessage(err: unknown): string {
    if (err instanceof Object && 'error' in err) {
      const error = err as any;
      if (error.error?.message) {
        return error.error.message;
      }
      if (error.message) {
        return error.message;
      }
    }
    return 'Failed to load trade report. Please try again.';
  }
}
