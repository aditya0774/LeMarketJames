import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TradeActivityByPeriod } from './trade-activity-by-period';
import { TradeReportService } from '../../../../shared/services/trade-report.service';

describe('TradeActivityByPeriod', () => {
  let component: TradeActivityByPeriod;
  let fixture: ComponentFixture<TradeActivityByPeriod>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TradeActivityByPeriod, HttpClientTestingModule],
      providers: [
        TradeReportService,
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TradeActivityByPeriod);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have period types available', () => {
    expect(component.periodTypes).toEqual(['DAY', 'WEEK', 'MONTH', 'YEAR']);
  });

  it('should initialize with DAY period type', () => {
    expect(component.periodType).toBe('DAY');
  });

  it('should initialize with default dates (last 7 days) and zero data', () => {
    expect(component.fromDate).toBeDefined();
    expect(component.toDate).toBeDefined();
    expect(component.data).toEqual([]);
    expect(component.loading).toBe(false);
    expect(component.error).toBeNull();
  });

  it('should change period type', () => {
    component.onPeriodTypeChange('WEEK');
    expect(component.periodType).toBe('WEEK');
  });

  it('should have loadReport method', () => {
    expect(component.loadReport).toBeDefined();
    expect(typeof component.loadReport).toBe('function');
  });

  it('should format period labels for DAY period type', () => {
    component.periodType = 'DAY';
    const formatted = component.formatPeriodLabel('2026-10-01');
    expect(formatted).toContain('October');
    expect(formatted).toContain('2026');
  });

  it('should format period labels for WEEK period type', () => {
    component.periodType = 'WEEK';
    const formatted = component.formatPeriodLabel('2026-W41');
    expect(formatted).toContain('Week 41');
    expect(formatted).toContain('2026');
  });

  it('should format period labels for MONTH period type', () => {
    component.periodType = 'MONTH';
    const formatted = component.formatPeriodLabel('2026-10');
    expect(formatted).toContain('October');
    expect(formatted).toContain('2026');
  });

  it('should format period labels for YEAR period type', () => {
    component.periodType = 'YEAR';
    const formatted = component.formatPeriodLabel('2026');
    expect(formatted).toBe('2026');
  });

  it('should render heading in template', () => {
    // Don't call fixture.detectChanges() yet because it will trigger ngOnInit
    // and make HTTP requests. Just render the template.
    fixture.detectChanges();
    const heading = fixture.nativeElement.querySelector('h1');
    expect(heading?.textContent).toContain('Trade activity by period');
    // Respond to the HTTP request that ngOnInit triggered
    const req = httpMock.expectOne(req => req.url.includes('/api/v1/reports/trades'));
    req.flush({ data: [], generatedAt: new Date().toISOString() });
  });

  it('should render period type buttons', () => {
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('.tab-button');
    expect(buttons.length).toBe(4);
    // Respond to the HTTP request
    const req = httpMock.expectOne(req => req.url.includes('/api/v1/reports/trades'));
    req.flush({ data: [], generatedAt: new Date().toISOString() });
  });
});
