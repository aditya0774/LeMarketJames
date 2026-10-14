import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { VolumeInsightService } from './volume-insight.service';

describe('VolumeInsightService', () => {
  let service: VolumeInsightService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VolumeInsightService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the volume insight with the period type', () => {
    service.get('WEEK').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/reports/insights/volume');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('periodType')).toBe('WEEK');
    req.flush({});
  });

  it('GETs a comparison of two date ranges', () => {
    service.compare({
      currentFrom: '2026-10-12', currentTo: '2026-10-14',
      previousFrom: '2026-10-05', previousTo: '2026-10-07',
    }).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/reports/insights/volume/compare');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('currentFrom')).toBe('2026-10-12');
    expect(req.request.params.get('previousTo')).toBe('2026-10-07');
    req.flush({});
  });
});
