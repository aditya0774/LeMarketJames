import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { VolumeComparisonResponse } from '../../../shared/models/volume-insight.model';
import { VolumeInsightTile } from './volume-insight-tile';

const COMPARE = '/api/v1/reports/insights/volume/compare';
const PING = '/api/v1/reports/ping';

/** The C6 example, with the change and previous period adjustable per test. */
function comparison(overrides: Partial<VolumeComparisonResponse> = {}): VolumeComparisonResponse {
  return {
    current: { startDate: '2026-10-05', endDate: '2026-10-11', tradeCount: 40, shareVolume: 1200, grossAmount: 54000 },
    previous: { startDate: '2026-09-28', endDate: '2026-10-04', tradeCount: 32, shareVolume: 1000, grossAmount: 41000 },
    change: { shareVolume: 200, shareVolumePercent: 20, grossAmount: 13000, grossAmountPercent: 31.71 },
    generatedAt: '2026-10-12T14:00:00Z',
    ...overrides,
  };
}

describe('VolumeInsightTile', () => {
  let fixture: ComponentFixture<VolumeInsightTile>;
  let httpMock: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const card = (key: string) => el().querySelector(`[data-period="${key}"]`) as HTMLElement;

  /** The three preset requests, in the order the tile asks for them: today, this week, last week. */
  const presetRequests = (): TestRequest[] => httpMock.match((r) => r.url === COMPARE);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VolumeInsightTile],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VolumeInsightTile);
    fixture.detectChanges();
    // The tile asks for the reports time zone first; the cards load once it has it.
    httpMock.expectOne(PING).flush({ success: true, service: 'reporting-service', timeZone: 'America/New_York' });
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  async function flushPresets(...responses: VolumeComparisonResponse[]) {
    const requests = presetRequests();
    requests.forEach((r, i) => r.flush(responses[i] ?? comparison()));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('asks for today, this week and last week, each against the period before, and shows loading', () => {
    expect(card('TODAY').textContent).toContain('Loading');
    expect(card('THIS_WEEK').textContent).toContain('Loading');
    expect(card('LAST_WEEK').textContent).toContain('Loading');

    const requests = presetRequests();
    expect(requests.length).toBe(3);
    for (const r of requests) {
      for (const p of ['currentFrom', 'currentTo', 'previousFrom', 'previousTo']) {
        expect(r.request.params.get(p)).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      }
    }
    // Today is one day against yesterday; last week ends the day before this week starts.
    const [today, thisWeek, lastWeek] = requests.map((r) => r.request.params);
    expect(today.get('currentFrom')).toBe(today.get('currentTo'));
    expect(lastWeek.get('currentTo')! < thisWeek.get('currentFrom')!).toBe(true);
  });

  it('shows volume, change, date ranges and last-refreshed time on every card', async () => {
    await flushPresets();

    for (const [key, label] of [['TODAY', 'Today'], ['THIS_WEEK', 'This week'], ['LAST_WEEK', 'Last week']]) {
      const c = card(key);
      expect(c.textContent).toContain(label);
      expect(c.querySelector('.range')!.textContent).toContain('2026-10-05 – 2026-10-11');
      expect(c.querySelector('.volume')!.textContent).toContain('1,200');
      expect(c.querySelector('.change')!.textContent).toContain('+200');
      expect(c.querySelector('.change')!.textContent).toContain('+20.00%');
      expect(c.querySelector('.change')!.classList).toContain('up');
      expect(c.querySelector('.previous')!.textContent).toContain('1,000');
      expect(c.querySelector('.refreshed')!.textContent).toContain('Last refreshed');
    }
  });

  it('marks a drop as down with a minus sign', async () => {
    const drop = comparison({
      current: { startDate: '2026-10-05', endDate: '2026-10-11', tradeCount: 5, shareVolume: 750, grossAmount: 10000 },
      change: { shareVolume: -250, shareVolumePercent: -25, grossAmount: -10000, grossAmountPercent: -50 },
    });
    await flushPresets(drop, drop, drop);

    const change = card('THIS_WEEK').querySelector('.change')!;
    expect(change.textContent).toContain('-250');
    expect(change.textContent).toContain('-25.00%');
    expect(change.classList).toContain('down');
  });

  it('says there is nothing to compare with when the previous period had no trades', async () => {
    const none = comparison({
      change: { shareVolume: 100, shareVolumePercent: null, grossAmount: 500, grossAmountPercent: null },
    });
    await flushPresets(none, none, none);

    expect(card('TODAY').querySelector('.change')!.textContent).toContain('n/a');
  });

  it('shows an error on the card that failed without hiding the others', async () => {
    const [today, thisWeek, lastWeek] = presetRequests();
    today.flush(comparison());
    thisWeek.flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    lastWeek.flush(comparison());
    await fixture.whenStable();
    fixture.detectChanges();

    expect(card('THIS_WEEK').querySelector('[role="alert"]')).not.toBeNull();
    expect(card('TODAY').querySelector('.volume')).not.toBeNull();
    expect(card('LAST_WEEK').querySelector('.volume')).not.toBeNull();
  });

  it('Refresh asks for all three again', async () => {
    await flushPresets();

    (el().querySelector('button.refresh') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(presetRequests().length).toBe(3);
  });

  it('a background reload keeps the figures on screen', async () => {
    await flushPresets();

    fixture.componentInstance.refresh(true);
    fixture.detectChanges();

    expect(card('TODAY').textContent).not.toContain('Loading');
    const requests = presetRequests();
    requests[0].flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    requests[1].flush(comparison());
    requests[2].flush(comparison());
    fixture.detectChanges();

    expect(card('TODAY').querySelector('.volume')).not.toBeNull();
    expect(card('TODAY').querySelector('[role="alert"]')).toBeNull();
  });

  it('compares the two ranges the user picked', async () => {
    await flushPresets();

    fixture.componentInstance.pick.setValue({
      currentFrom: new Date(2026, 9, 1),
      currentTo: new Date(2026, 9, 7),
      previousFrom: new Date(2026, 8, 1),
      previousTo: new Date(2026, 8, 7),
    });
    (el().querySelector('button.compare') as HTMLButtonElement).click();
    fixture.detectChanges();

    const [custom] = presetRequests();
    expect(custom.request.params.get('currentFrom')).toBe('2026-10-01');
    expect(custom.request.params.get('currentTo')).toBe('2026-10-07');
    expect(custom.request.params.get('previousFrom')).toBe('2026-09-01');
    expect(custom.request.params.get('previousTo')).toBe('2026-09-07');
    custom.flush(comparison());
    await fixture.whenStable();
    fixture.detectChanges();

    expect(card('CUSTOM').querySelector('.volume')!.textContent).toContain('1,200');
  });

  it('asks for the missing dates instead of calling the server', async () => {
    await flushPresets();

    fixture.componentInstance.pick.patchValue({ previousTo: null });
    (el().querySelector('button.compare') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(presetRequests().length).toBe(0);
    expect(card('CUSTOM').querySelector('[role="alert"]')).not.toBeNull();
  });
});

describe('VolumeInsightTile without the reports time zone', () => {
  it("still loads, using the browser's day, when the zone cannot be read", async () => {
    await TestBed.configureTestingModule({
      imports: [VolumeInsightTile],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(VolumeInsightTile);
    fixture.detectChanges();

    httpMock.expectOne(PING).flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    const requests = httpMock.match((r) => r.url === COMPARE);
    expect(requests.length).toBe(3);
    requests.forEach((r) => r.flush(comparison()));
    httpMock.verify();
  });
});
