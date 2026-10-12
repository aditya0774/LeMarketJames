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

import { ActivityByClientSegment } from './activity-by-client-segment';
import {
  ReportingService,
  SegmentActivityRow,
} from '../../../../core/reports/reporting.service';

describe('ActivityByClientSegment Component', () => {
  let httpTestingController: HttpTestingController;
  let component: ActivityByClientSegment;
  let fixture: any;

  const mockSegmentData: SegmentActivityRow[] = [
    {
      segment: 'ACTIVE_TRADER',
      activeClients: 12,
      tradeCount: 126,
      totalTradedValue: 341250.5,
    },
    {
      segment: 'RETAIL',
      activeClients: 42,
      tradeCount: 180,
      totalTradedValue: 215090.0,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ActivityByClientSegment,
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
      providers: [ReportingService, provideRouter([])],
    }).compileComponents();

    httpTestingController = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ActivityByClientSegment);
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

      fixture.detectChanges();
      httpTestingController.expectOne((req) => req.url.includes('/api/v1/reports/activity')).flush({ success: true, data: [] });

      const startDate = component.dateRangeForm.get('startDate')?.value;
      const endDate = component.dateRangeForm.get('endDate')?.value;

      expect(startDate).toBeTruthy();
      expect(endDate).toBeTruthy();

      const startDiff = Math.abs((startDate as Date).getTime() - thirtyDaysAgo.getTime());
      const endDiff = Math.abs((endDate as Date).getTime() - today.getTime());
      expect(startDiff).toBeLessThan(1000 * 60 * 60 * 24);
      expect(endDiff).toBeLessThan(1000 * 60 * 60 * 24);
    });

    it('loads report data on init', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      expect(req.request.method).toBe('GET');

      req.flush({ success: true, data: mockSegmentData });
      fixture.detectChanges();

      expect((component as any).reportData()).toEqual(mockSegmentData);
      expect((component as any).error()).toBeNull();
    });
  });

  describe('Date Range Filtering', () => {
    it('sends correct date parameters to API', () => {
      fixture.detectChanges();
      httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity')).flush({ success: true, data: mockSegmentData });

      component.dateRangeForm.patchValue({
        startDate: new Date('2026-10-01'),
        endDate: new Date('2026-10-07'),
      });
      component['onDateRangeChange']();

      const newReq = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      expect(newReq.request.params.get('startDate')).toBe('2026-10-01');
      expect(newReq.request.params.get('endDate')).toBe('2026-10-07');
      newReq.flush({ success: true, data: mockSegmentData });
    });

    it('updates report data when date range changes', () => {
      fixture.detectChanges();

      let req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: mockSegmentData });
      expect((component as any).reportData().length).toBe(2);

      const newData: SegmentActivityRow[] = [
        {
          segment: 'HIGH_NET_WORTH',
          activeClients: 5,
          tradeCount: 31,
          totalTradedValue: 510000,
        },
      ];

      component.dateRangeForm.patchValue({
        startDate: new Date('2026-09-01'),
        endDate: new Date('2026-09-30'),
      });
      component['onDateRangeChange']();

      req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: newData });
      fixture.detectChanges();

      expect((component as any).reportData()).toEqual(newData);
    });
  });

  describe('Error Handling', () => {
    it('displays 403 error message for unauthorized access', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      fixture.detectChanges();

      expect((component as any).error()).toContain('do not have permission');
      expect((component as any).isLoading()).toBe(false);
    });

    it('displays 401 error message for expired session', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      fixture.detectChanges();

      expect((component as any).error()).toContain('session has expired');
      expect((component as any).isLoading()).toBe(false);
    });

    it('displays 400 error message for invalid date range', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();

      expect((component as any).error()).toContain('Invalid date range');
      expect((component as any).isLoading()).toBe(false);
    });

    it('displays generic error for network errors', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush('Network error', { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();

      expect((component as any).error()).toContain('Unable to load report');
      expect((component as any).isLoading()).toBe(false);
    });

    it('clears error on successful retry', () => {
      fixture.detectChanges();

      let req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush('Error', { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();

      expect((component as any).error()).toBeTruthy();

      component['onRetry']();

      req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: mockSegmentData });
      fixture.detectChanges();

      expect((component as any).error()).toBeNull();
      expect((component as any).reportData()).toEqual(mockSegmentData);
    });
  });

  describe('Loading State', () => {
    it('sets loading state while fetching', () => {
      fixture.detectChanges();
      expect((component as any).isLoading()).toBe(true);

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: mockSegmentData });
      fixture.detectChanges();

      expect((component as any).isLoading()).toBe(false);
    });
  });

  describe('Proportional chart', () => {
    it('renders unequal and zero values on one scale alongside the segment table', () => {
      fixture.detectChanges();
      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: [
        { segment: 'RETAIL', activeClients: 2, tradeCount: 4, totalTradedValue: 100 },
        { segment: 'ACTIVE_TRADER', activeClients: 1, tradeCount: 2, totalTradedValue: 50 },
        { segment: 'HIGH_NET_WORTH', activeClients: 0, tradeCount: 0, totalTradedValue: 0 },
      ] });
      fixture.detectChanges();

      const bars = fixture.nativeElement.querySelectorAll('.bar-fill');
      expect(Array.from(bars).map((bar: any) => bar.style.width)).toEqual(['100%', '50%', '0%']);
      expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(3);
      expect(fixture.nativeElement.textContent).toContain('33.3% of total');
    });

    it('renders all-zero values without invalid widths', () => {
      fixture.detectChanges();
      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: [
        { segment: 'RETAIL', activeClients: 0, tradeCount: 0, totalTradedValue: 0 },
      ] });
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.bar-fill').style.width).toBe('0%');
      expect(fixture.nativeElement.textContent).toContain('0.0% of total');
    });
  });

  describe('Data Display', () => {
    it('displays report data', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: mockSegmentData });
      fixture.detectChanges();

      expect((component as any).reportData()).toEqual(mockSegmentData);
    });

    it('handles empty data response', () => {
      fixture.detectChanges();

      const req = httpTestingController.expectOne((r) => r.url.includes('/api/v1/reports/activity'));
      req.flush({ success: true, data: [] });
      fixture.detectChanges();

      expect((component as any).reportData()).toEqual([]);
    });
  });
});
