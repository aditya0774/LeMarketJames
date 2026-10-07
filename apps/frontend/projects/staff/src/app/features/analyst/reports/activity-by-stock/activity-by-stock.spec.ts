import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';

import { ActivityByStock } from './activity-by-stock.component';
import { ReportingService, TradesByStockRow } from '../../../../core/reports/reporting.service';

describe('ActivityByStock Component', () => {
  let httpTestingController: HttpTestingController;
  let reportingService: ReportingService;
  let component: ActivityByStock;
  let fixture: any;

  const mockTradesData: TradesByStockRow[] = [
    { symbol: 'AAPL', totalQuantity: 150, totalGrossAmount: 22500.75, buyCount: 8, sellCount: 5 },
    { symbol: 'MSFT', totalQuantity: 200, totalGrossAmount: 50200.00, buyCount: 10, sellCount: 3 },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ActivityByStock,
        ReactiveFormsModule,
        MatDatepickerModule,
        MatNativeDateModule,
        MatInputModule,
        MatFormFieldModule,
        MatButtonModule,
        MatProgressSpinnerModule,
        MatCardModule,
        HttpClientTestingModule,
      ],
      providers: [
        ReportingService,
        provideRouter([]),
      ],
    }).compileComponents();

    httpTestingController = TestBed.inject(HttpTestingController);
    reportingService = TestBed.inject(ReportingService);
    fixture = TestBed.createComponent(ActivityByStock);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('Initialization', () => {
    it('sets default date range to last 30 days on init', () => {
      const today = new Date();
      const thirtyDaysAgo = new Date(today);
      thirtyDaysAgo.setDate(today.getDate() - 30);

      fixture.detectChanges(); // triggers ngOnInit

      const startDate = component.dateRangeForm.get('startDate')?.value;
      const endDate = component.dateRangeForm.get('endDate')?.value;

      expect(startDate).toBeTruthy();
      expect(endDate).toBeTruthy();
      // Allow 1-day tolerance due to test execution time
      const startDiff = Math.abs((startDate as Date).getTime() - thirtyDaysAgo.getTime());
      const endDiff = Math.abs((endDate as Date).getTime() - today.getTime());
      expect(startDiff).toBeLessThan(1000 * 60 * 60 * 24); // within 1 day
      expect(endDiff).toBeLessThan(1000 * 60 * 60 * 24); // within 1 day
    });

    it('loads report data on init', () => {
      fixture.detectChanges(); // triggers ngOnInit

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      expect(req.request.method).toBe('GET');

      req.flush({ success: true, data: mockTradesData });
      fixture.detectChanges();

      expect(component.tradesData()).toEqual(mockTradesData);
      expect(component.error()).toBeNull();
    });
  });

  describe('Date Range Filtering', () => {
    it('sends correct date parameters to API', () => {
      fixture.detectChanges(); // triggers ngOnInit

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: mockTradesData });

      component.dateRangeForm.patchValue({
        startDate: new Date('2026-10-01'),
        endDate: new Date('2026-10-07'),
      });
      component['onDateRangeChange']();

      const newReq = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      expect(newReq.request.params.get('startDate')).toBe('2026-10-01');
      expect(newReq.request.params.get('endDate')).toBe('2026-10-07');

      newReq.flush({ success: true, data: mockTradesData });
    });

    it('updates data when date range changes', () => {
      fixture.detectChanges();

      let req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: mockTradesData });
      expect(component.tradesData().length).toBe(2);

      const newData: TradesByStockRow[] = [
        { symbol: 'TSLA', totalQuantity: 100, totalGrossAmount: 15000.00, buyCount: 5, sellCount: 2 },
      ];

      component.dateRangeForm.patchValue({
        startDate: new Date('2026-09-01'),
        endDate: new Date('2026-09-30'),
      });
      component['onDateRangeChange']();

      req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: newData });
      fixture.detectChanges();

      expect(component.tradesData()).toEqual(newData);
    });
  });

  describe('Error Handling', () => {
    it('displays 403 error message for unauthorized access', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      fixture.detectChanges();

      expect(component.error()).toContain('do not have permission');
      expect(component.isLoading()).toBe(false);
    });

    it('displays 401 error message for expired session', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      fixture.detectChanges();

      expect(component.error()).toContain('session has expired');
      expect(component.isLoading()).toBe(false);
    });

    it('displays 400 error message for invalid date range', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();

      expect(component.error()).toContain('Invalid date range');
      expect(component.isLoading()).toBe(false);
    });

    it('displays generic error for network errors', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Network error', { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();

      expect(component.error()).toContain('Unable to load report');
      expect(component.isLoading()).toBe(false);
    });

    it('clears error on successful retry', () => {
      fixture.detectChanges();

      let req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush('Error', { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();

      expect(component.error()).toBeTruthy();

      component['onRetry']();

      req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: mockTradesData });
      fixture.detectChanges();

      expect(component.error()).toBeNull();
      expect(component.tradesData()).toEqual(mockTradesData);
    });
  });

  describe('Loading State', () => {
    it('sets loading state while fetching', () => {
      fixture.detectChanges();
      expect(component.isLoading()).toBe(true);

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: mockTradesData });
      fixture.detectChanges();

      expect(component.isLoading()).toBe(false);
    });
  });

  describe('Data Display', () => {
    it('displays trades data', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: mockTradesData });
      fixture.detectChanges();

      expect(component.tradesData()).toEqual(mockTradesData);
    });

    it('handles empty data response', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne(req => req.url.includes('/api/v1/reports/trades-by-stock'));
      req.flush({ success: true, data: [] });
      fixture.detectChanges();

      expect(component.tradesData()).toEqual([]);
    });
  });
});

