import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OvernightReportsService } from './overnight-reports.service';
import { OvernightReportsResponse } from '../models/overnight-report.model';

describe('OvernightReportsService', () => {
  let service: OvernightReportsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OvernightReportsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the overnight reports list and passes the response through', () => {
    const body: OvernightReportsResponse = { success: true, timeZone: 'America/New_York', data: [] };
    let received: OvernightReportsResponse | undefined;
    service.list().subscribe((r) => (received = r));

    const req = httpMock.expectOne('/api/v1/reports/overnight');
    expect(req.request.method).toBe('GET');
    req.flush(body);

    expect(received).toEqual(body);
  });
});
