import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActiveClientTrendService } from './active-client-trend.service';

describe('ActiveClientTrendService', () => {
  let service: ActiveClientTrendService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ActiveClientTrendService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the active-client trend and passes the response through', () => {
    const body = {
      startDate: '2026-09-15',
      endDate: '2026-10-14',
      days: [{ date: '2026-09-15', activeClients: 12 }],
      generatedAt: '2026-10-14T14:00:00Z',
    };
    let result: unknown;
    service.get().subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/v1/reports/insights/active-clients');
    expect(req.request.method).toBe('GET');
    req.flush(body);
    expect(result).toEqual(body);
  });
});
