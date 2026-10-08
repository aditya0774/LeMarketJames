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
      <p class="subtitle">View aggregated trade statistics by day, week, month, or year</p>

      <!-- Period Type Selector (Tabs) -->
      <div class="period-selector">
        <label>Period Type:</label>
        <div class="tab-group">
          @for (type of periodTypes; track type) {
            <button
              [class.active]="periodType === type"
              (click)="onPeriodTypeChange(type)"
              class="tab-button"
              [title]="getPeriodTypeHint(type)"
            >
              {{ type }}
            </button>
          }
        </div>
      </div>

      <!-- Date Range Inputs -->
      <div class="date-range">
        <div class="form-group">
          <label for="fromDate">Start Date:</label>
          <input
            type="date"
            id="fromDate"
            [(ngModel)]="fromDate"
            (change)="onDateChange()"
            class="date-input"
            aria-label="Start date for report"
          />
        </div>
        <div class="form-group">
          <label for="toDate">End Date:</label>
          <input
            type="date"
            id="toDate"
            [(ngModel)]="toDate"
            (change)="onDateChange()"
            class="date-input"
            aria-label="End date for report"
          />
        </div>
        <div class="preset-buttons">
          <button (click)="setPresetDates('week')" class="preset-btn">Last 7 days</button>
          <button (click)="setPresetDates('month')" class="preset-btn">This month</button>
          <button (click)="setPresetDates('quarter')" class="preset-btn">This quarter</button>
        </div>
      </div>

      <!-- Loading State -->
      @if (loading) {
        <div class="loading">
          <div class="spinner"></div>
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
          <p>📊 No trades found in this period.</p>
          <small>Try adjusting your date range or period type.</small>
        </div>
      }

      @if (!loading && !error && data.length > 0) {
        <div class="results-section">
          <div class="results-header">
            <h2>Results: {{ data.length }} periods, {{ totalTradeCount }} trades</h2>
          </div>
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
      margin-bottom: 8px;
      color: #222;
      font-size: 28px;
    }

    .subtitle {
      margin-bottom: 24px;
      color: #666;
      font-size: 14px;
      font-style: italic;
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
      flex-wrap: wrap;
    }

    .tab-button {
      padding: 10px 20px;
      border: 2px solid #ddd;
      background: #f5f5f5;
      cursor: pointer;
      border-radius: 6px;
      transition: all 0.2s;
      font-weight: 500;
      font-size: 14px;
      min-width: 70px;
      text-align: center;
    }

    .tab-button:hover {
      background: #e8e8e8;
      border-color: #bbb;
    }

    .tab-button.active {
      background: #007bff;
      color: white;
      border-color: #0056b3;
      box-shadow: 0 2px 4px rgba(0, 123, 255, 0.3);
    }

    .date-range {
      display: flex;
      gap: 20px;
      margin-bottom: 24px;
      align-items: flex-end;
      flex-wrap: wrap;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    .form-group label {
      margin-bottom: 6px;
      font-weight: 600;
      color: #222;
      font-size: 14px;
    }

    .date-input {
      padding: 10px 12px;
      border: 2px solid #ddd;
      border-radius: 6px;
      font-size: 14px;
      transition: border-color 0.2s;
      min-width: 180px;
    }

    .date-input:focus {
      outline: none;
      border-color: #007bff;
      box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.1);
    }

    .preset-buttons {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .preset-btn {
      padding: 10px 14px;
      background: #e7f3ff;
      border: 1px solid #b3d9ff;
      color: #0056b3;
      cursor: pointer;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 500;
      transition: all 0.2s;
    }

    .preset-btn:hover {
      background: #cce5ff;
      border-color: #80b3ff;
    }

    .loading {
      padding: 60px 40px;
      text-align: center;
      color: #666;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 4px solid #f3f3f3;
      border-top: 4px solid #007bff;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto 16px;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    .loading p {
      margin: 0;
      color: #666;
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
      padding: 60px 40px;
      text-align: center;
      color: #666;
    }

    .empty-state p {
      margin: 0 0 10px 0;
      font-size: 16px;
    }

    .empty-state small {
      display: block;
      color: #999;
      font-size: 13px;
    }

    .results-section {
      margin-bottom: 20px;
    }

    .results-header {
      margin-bottom: 16px;
      padding-bottom: 12px;
      border-bottom: 2px solid #e0e0e0;
    }

    .results-header h2 {
      margin: 0;
      font-size: 16px;
      color: #222;
      font-weight: 600;
    }

    .table-container {
      margin-bottom: 20px;
      overflow-x: auto;
      border-radius: 6px;
      border: 1px solid #e0e0e0;
    }

    .results-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }

    .results-table thead {
      background: #f8f9fa;
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
      font-weight: 600;
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

  /**
   * Get tooltip hint for each period type.
   */
  getPeriodTypeHint(type: string): string {
    const hints: { [key: string]: string } = {
      DAY: 'Daily: Aggregates trades by individual calendar day',
      WEEK: 'Weekly: Aggregates trades by ISO 8601 week (Mon–Sun)',
      MONTH: 'Monthly: Aggregates trades by calendar month',
      YEAR: 'Yearly: Aggregates trades by calendar year',
    };
    return hints[type] || '';
  }

  /**
   * Set date range using preset options.
   */
  setPresetDates(preset: string): void {
    const today = new Date();
    const startDate = new Date();

    switch (preset) {
      case 'week':
        startDate.setDate(today.getDate() - 7);
        break;
      case 'month':
        startDate.setMonth(today.getMonth());
        startDate.setDate(1);
        break;
      case 'quarter':
        const quarter = Math.floor(today.getMonth() / 3);
        startDate.setMonth(quarter * 3);
        startDate.setDate(1);
        break;
    }

    this.fromDate = this.formatDateForInput(startDate);
    this.toDate = this.formatDateForInput(today);
    this.loadReport();
  }

  /**
   * Computed property: total trade count across all periods.
   */
  get totalTradeCount(): number {
    return this.data.reduce((sum, period) => sum + period.tradeCount, 0);
  }
}
