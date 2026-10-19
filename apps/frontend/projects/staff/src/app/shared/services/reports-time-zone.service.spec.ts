import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReportsTimeZoneService } from './reports-time-zone.service';

describe('ReportsTimeZoneService', () => {
  let service: ReportsTimeZoneService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReportsTimeZoneService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('reads the zone from the ping once and shares it', () => {
    const zones: string[] = [];
    service.timeZone$.subscribe((z) => zones.push(z));
    service.timeZone$.subscribe((z) => zones.push(z));

    httpMock.expectOne('/api/v1/reports/ping').flush({ success: true, timeZone: 'America/New_York' });

    expect(zones).toEqual(['America/New_York', 'America/New_York']);
  });

  it('asks again after a failed ping', () => {
    service.timeZone$.subscribe({ error: () => undefined });
    httpMock.expectOne('/api/v1/reports/ping').flush('down', { status: 503, statusText: 'Unavailable' });

    let zone: string | undefined;
    service.timeZone$.subscribe((z) => (zone = z));
    httpMock.expectOne('/api/v1/reports/ping').flush({ success: true, timeZone: 'UTC' });

    expect(zone).toBe('UTC');
  });
});
