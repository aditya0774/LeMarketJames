import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReactiveFormsModule } from '@angular/forms';
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
import { provideRouter } from '@angular/router';

import { TradeActivityByPeriod } from './trade-activity-by-period';
import { ReportingService, TradesByStockRow, TradesReportResponse } from '../../../../core/reports/reporting.service';

describe('TradeActivityByPeriod Component', () => {
  let component: TradeActivityByPeriod;
  let fixture: ComponentFixture<TradeActivityByPeriod>;
  let httpMock: HttpTestingController;

  // Real seed_active persona trades aggregated from database/schema/011_seed_test_data.sql
  // 12-month history with actual LeBron-themed stock data
  const mockTradesData: TradesByStockRow[] = [
    {
      // BUY 20 @ 190 + SELL 5 @ 205 + BUY 5 @ 230 (12, 8, 1 months ago)
      symbol: 'AAPL',
      totalQuantity: 20,
      totalGrossAmount: 6950.00,
      buyCount: 2,
      sellCount: 1,
    },
    {
      // BUY 10 @ 410 + SELL 4 @ 430 (11, 2 months ago)
      symbol: 'MSFT',
      totalQuantity: 6,
      totalGrossAmount: 5820.00,
      buyCount: 1,
      sellCount: 1,
    },
    {
      // BUY 15 @ 120 + SELL 5 @ 135 (10, 5 months ago)
      symbol: 'NVDA',
      totalQuantity: 10,
      totalGrossAmount: 2475.00,
      buyCount: 1,
      sellCount: 1,
    },
    {
      // BUY 12 @ 165 (9 months ago)
      symbol: 'GOOGL',
      totalQuantity: 12,
      totalGrossAmount: 1980.00,
      buyCount: 1,
      sellCount: 0,
    },
    {
      // BUY 10 @ 210 (6 months ago)
      symbol: 'JPM',
      totalQuantity: 10,
      totalGrossAmount: 2100.00,
      buyCount: 1,
      sellCount: 0,
    },
    {
      // BUY 10 @ 250 + SELL 10 @ 275 (2 years, 18 months ago) - closed position
      symbol: 'V',
      totalQuantity: 0,
      totalGrossAmount: 5250.00,
      buyCount: 1,
      sellCount: 1,
    },
  ];

  const mockResponse: TradesReportResponse = {
    success: true,
    data: mockTradesData,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        TradeActivityByPeriod,
        HttpClientTestingModule,
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
      providers: [ReportingService, provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TradeActivityByPeriod);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize with MONTH period type', () => {
      expect(component.periodType()).toBe('MONTH');
    });

    it('should initialize with default date range (last 30 days)', () => {
      fixture.detectChanges();

      const startDate = component.startDate();
      const endDate = component.endDate();

      expect(startDate).toBeTruthy();
      expect(endDate).toBeTruthy();

      // Calculate expected difference (30 days)
      const diff = endDate!.getTime() - startDate!.getTime();
      const daysDiff = Math.floor(diff / (1000 * 60 * 60 * 24));

      expect(daysDiff).toBeCloseTo(30, 1); // Allow 1 day difference due to timing
    });

    it('should set form controls with default dates on init', () => {
      fixture.detectChanges();

      const startFormDate = component.dateRangeForm.get('startDate')?.value;
      const endFormDate = component.dateRangeForm.get('endDate')?.value;

      expect(startFormDate).toBeTruthy();
      expect(endFormDate).toBeTruthy();
    });

    it('should load report on init', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      expect(req.request.method).toBe('GET');

      req.flush(mockResponse);
      fixture.detectChanges();

      expect(component.reportData()).toEqual(mockTradesData);
      expect(component.isLoading()).toBe(false);
    });
  });

  describe('Period Type Changes', () => {
    it('should change period type to DAY and adjust date range', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      component.onPeriodTypeChange('DAY');
      fixture.detectChanges();

      expect(component.periodType()).toBe('DAY');

      // Verify another request was made (date range reload)
      req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      expect(req.request.method).toBe('GET');

      req.flush(mockResponse);
    });

    it('should change period type to WEEK and adjust date range', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      component.onPeriodTypeChange('WEEK');
      fixture.detectChanges();

      expect(component.periodType()).toBe('WEEK');

      req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);
    });

    it('should change period type to YEAR and adjust date range', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      component.onPeriodTypeChange('YEAR');
      fixture.detectChanges();

      expect(component.periodType()).toBe('YEAR');

      req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);
    });
  });

  describe('Date Range Changes', () => {
    it('should send correct startDate and endDate parameters to API', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      const newStartDate = new Date('2026-10-01');
      const newEndDate = new Date('2026-10-07');

      component.dateRangeForm.patchValue({
        startDate: newStartDate,
        endDate: newEndDate,
      });

      component.onDateRangeChange();
      fixture.detectChanges();

      req = httpMock.expectOne((request) => {
        const url = request.url;
        const startParam = request.params.get('startDate');
        const endParam = request.params.get('endDate');

        return (
          url.includes('/api/v1/reports/trades-by-stock') &&
          startParam === '2026-10-01' &&
          endParam === '2026-10-07'
        );
      });

      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should validate date range and show error if startDate > endDate', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      const startDate = new Date('2026-10-10');
      const endDate = new Date('2026-10-01');

      component.dateRangeForm.patchValue({
        startDate,
        endDate,
      });

      component.onDateRangeChange();
      fixture.detectChanges();

      expect(component.error()).toContain('Start date must be before or equal to end date');
      // No additional HTTP request should be made
      httpMock.expectNone((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
    });

    it('should show error if either date is missing', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      component.dateRangeForm.patchValue({
        startDate: null,
        endDate: new Date('2026-10-07'),
      });

      component.onDateRangeChange();
      fixture.detectChanges();

      expect(component.error()).toContain('Both start and end dates are required');
    });
  });

  describe('Error Handling', () => {
    it('should handle 401 Unauthorized (expired session)', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

      fixture.detectChanges();

      expect(component.isLoading()).toBe(false);
      expect(component.error()).toContain('session has expired');
    });

    it('should handle 403 Forbidden (access denied)', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

      fixture.detectChanges();

      expect(component.isLoading()).toBe(false);
      expect(component.error()).toContain('do not have permission');
    });

    it('should handle 400 Bad Request (invalid date range)', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });

      fixture.detectChanges();

      expect(component.isLoading()).toBe(false);
      expect(component.error()).toContain('Invalid date range');
    });

    it('should handle generic errors', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Internal Server Error', { status: 500, statusText: 'Internal Server Error' });

      fixture.detectChanges();

      expect(component.isLoading()).toBe(false);
      expect(component.error()).toBeTruthy();
      expect(component.error()).toContain('An error occurred');
    });
  });

  describe('Data Display', () => {
    it('should display report data in table', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      fixture.detectChanges();

      expect(component.reportData()).toEqual(mockTradesData);
      expect(component.hasData()).toBe(true);
    });

    it('should display empty state when no data', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: [] });

      fixture.detectChanges();

      expect(component.reportData().length).toBe(0);
      expect(component.showEmpty()).toBe(true);
      expect(component.hasData()).toBe(false);
    });

    it('should render all 5 columns in table', () => {
      fixture.detectChanges();

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      fixture.detectChanges();

      expect(component.displayedColumns.length).toBe(5);
      expect(component.displayedColumns).toContain('symbol');
      expect(component.displayedColumns).toContain('totalQuantity');
      expect(component.displayedColumns).toContain('totalGrossAmount');
      expect(component.displayedColumns).toContain('buyCount');
      expect(component.displayedColumns).toContain('sellCount');
    });
  });

  describe('Formatting', () => {
    it('should format currency correctly', () => {
      const formatted = component.formatCurrency(6950.00);

      expect(formatted).toBe('$6,950.00');
    });

    it('should format currency with thousands separator', () => {
      const formatted = component.formatCurrency(5820.00);

      expect(formatted).toBe('$5,820.00');
    });

    it('should format small currency values', () => {
      const formatted = component.formatCurrency(2100.00);

      expect(formatted).toBe('$2,100.00');
    });

    it('should format date correctly', () => {
      const date = new Date('2026-10-07');
      const formatted = component.formatDateForDisplay(date);

      expect(formatted).toContain('Oct');
      expect(formatted).toContain('7');
      expect(formatted).toContain('2026');
    });
  });

  describe('Loading State', () => {
    it('should set isLoading to true during request', () => {
      fixture.detectChanges();

      expect(component.isLoading()).toBe(true);

      const req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      fixture.detectChanges();

      expect(component.isLoading()).toBe(false);
    });

    it('should clear error state when loading starts', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      // Trigger error
      component.dateRangeForm.patchValue({
        startDate: new Date('2026-10-10'),
        endDate: new Date('2026-10-01'),
      });

      component.onDateRangeChange();
      fixture.detectChanges();

      expect(component.error()).toBeTruthy();

      // Load a valid report
      component.dateRangeForm.patchValue({
        startDate: new Date('2026-10-01'),
        endDate: new Date('2026-10-10'),
      });

      component.onDateRangeChange();
      fixture.detectChanges();

      expect(component.error()).toBeNull();

      req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);
    });
  });

  describe('Refresh Functionality', () => {
    it('should reload report when refresh is called', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      fixture.detectChanges();

      component.refresh();
      fixture.detectChanges();

      req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      expect(req.request.method).toBe('GET');

      req.flush(mockResponse);
    });
  });

  describe('API Parameter Formatting', () => {
    it('should format dates as YYYY-MM-DD in API calls', () => {
      fixture.detectChanges();

      let req = httpMock.expectOne((request) => request.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush(mockResponse);

      const testDate = new Date('2026-10-07');

      component.dateRangeForm.patchValue({
        startDate: testDate,
        endDate: testDate,
      });

      component.onDateRangeChange();
      fixture.detectChanges();

      req = httpMock.expectOne((request) => {
        const startParam = request.params.get('startDate');

        return (
          request.url.includes('/api/v1/reports/trades-by-stock') &&
          startParam === '2026-10-07'
        );
      });

      expect(req.request.params.get('startDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(req.request.params.get('endDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);

      req.flush(mockResponse);
    });
  });
});
