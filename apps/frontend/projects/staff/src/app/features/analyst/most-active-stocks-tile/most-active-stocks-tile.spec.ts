import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MostActiveStocksResponse } from '../../../shared/models/most-active-stocks.model';
import { MOST_ACTIVE_STOCKS_REFRESH_MS, MostActiveStocksTile } from './most-active-stocks-tile';

const URL = '/api/v1/reports/insights/most-active-stocks';

/** This week's top stocks in the C6 shape, ranked by traded value. */
function week(stocks: MostActiveStocksResponse['stocks'] = [
  { rank: 1, symbol: 'AAPL', tradedValue: 125000, tradeCount: 24, shareVolume: 530 },
  { rank: 2, symbol: 'MSFT', tradedValue: 98000.5, tradeCount: 1200, shareVolume: 410.25 },
]): MostActiveStocksResponse {
  return { startDate: '2026-10-12', endDate: '2026-10-14', stocks, generatedAt: '2026-10-14T14:00:00Z' };
}

describe('MostActiveStocksTile', () => {
  let fixture: ComponentFixture<MostActiveStocksTile>;
  let httpMock: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const cells = (row: Element) => Array.from(row.querySelectorAll('td, th')).map(c => c.textContent!.trim());

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MostActiveStocksTile],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  /** Creates the tile; it asks for the figures as soon as it exists. */
  function create() {
    fixture = TestBed.createComponent(MostActiveStocksTile);
    fixture.detectChanges();
  }

  async function flush(body: MostActiveStocksResponse = week()) {
    httpMock.expectOne(URL).flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('asks for the insight once and shows loading', () => {
    create();

    expect(el().textContent).toContain('Loading');
    const request = httpMock.expectOne(URL);
    expect(request.request.method).toBe('GET');
    request.flush(week());
  });

  it("shows this week's dates and one row per stock, in rank order", async () => {
    create();
    await flush();

    expect(el().querySelector('.range')!.textContent).toContain('This week: 2026-10-12 – 2026-10-14');
    const rows = el().querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(cells(rows[0])).toEqual(['1', 'AAPL', '24', '530', '$125,000.00']);
    expect(cells(rows[1])).toEqual(['2', 'MSFT', '1,200', '410.25', '$98,000.50']);
  });

  it('shows the last-refreshed time', async () => {
    create();
    await flush();

    expect(el().querySelector('.refreshed')!.textContent).toContain('Last refreshed');
    expect(el().querySelector('.refreshed')!.textContent).toContain('2026');
  });

  it('says so when no stock traded this week', async () => {
    create();
    await flush(week([]));

    expect(el().querySelector('table')).toBeNull();
    expect(el().textContent).toContain('No filled trades this week.');
    expect(el().querySelector('.refreshed')).not.toBeNull();
  });

  it('shows an error when the request fails, and Refresh recovers', async () => {
    create();
    httpMock.expectOne(URL).flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')).not.toBeNull();
    expect(el().querySelector('table')).toBeNull();

    (el().querySelector('button.refresh') as HTMLButtonElement).click();
    fixture.detectChanges();
    await flush();

    expect(el().querySelector('[role="alert"]')).toBeNull();
    expect(el().querySelectorAll('tbody tr')).toHaveLength(2);
  });

  it('Refresh asks again', async () => {
    create();
    await flush();

    (el().querySelector('button.refresh') as HTMLButtonElement).click();
    fixture.detectChanges();

    const again = httpMock.expectOne(URL);
    expect(again.request.method).toBe('GET');
    again.flush(week());
  });

  it('reloads by itself every refresh interval', () => {
    // No whenStable here: it waits on timers, which fake timers hold still.
    vi.useFakeTimers();
    create();
    httpMock.expectOne(URL).flush(week());

    vi.advanceTimersByTime(MOST_ACTIVE_STOCKS_REFRESH_MS - 1);
    httpMock.expectNone(URL);

    vi.advanceTimersByTime(1);
    const reload = httpMock.expectOne(URL);
    expect(reload.request.method).toBe('GET');
    reload.flush(week());
  });

  it('a background reload keeps the figures on screen, even when it fails', async () => {
    create();
    await flush();

    fixture.componentInstance.refresh(true);
    fixture.detectChanges();
    expect(el().textContent).not.toContain('Loading');

    httpMock.expectOne(URL).flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(el().querySelectorAll('tbody tr')).toHaveLength(2);
    expect(el().querySelector('[role="alert"]')).toBeNull();
  });
});
