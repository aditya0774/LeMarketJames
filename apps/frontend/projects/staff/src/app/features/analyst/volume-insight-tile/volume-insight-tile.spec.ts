import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { VolumeInsightResponse } from '../../../shared/models/volume-insight.model';
import { VolumeInsightTile } from './volume-insight-tile';

/** The C6 example, with the change and previous period adjustable per test. */
function insight(overrides: Partial<VolumeInsightResponse> = {}): VolumeInsightResponse {
  return {
    periodType: 'WEEK',
    current: { startDate: '2026-10-05', endDate: '2026-10-11', tradeCount: 40, shareVolume: 1200, grossAmount: 54000 },
    previous: { startDate: '2026-09-28', endDate: '2026-10-04', tradeCount: 32, shareVolume: 1000, grossAmount: 41000 },
    inProgress: { startDate: '2026-10-12', endDate: '2026-10-12', tradeCount: 3, shareVolume: 75, grossAmount: 900 },
    change: { shareVolume: 200, shareVolumePercent: 20, grossAmount: 13000, grossAmountPercent: 31.71 },
    generatedAt: '2026-10-12T14:00:00Z',
    ...overrides,
  };
}

describe('VolumeInsightTile', () => {
  let fixture: ComponentFixture<VolumeInsightTile>;
  let httpMock: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const section = (period: string) => el().querySelector(`[data-period="${period}"]`) as HTMLElement;
  const request = (period: string): TestRequest =>
    httpMock.expectOne((r) => r.url === '/api/v1/reports/insights/volume' && r.params.get('periodType') === period);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VolumeInsightTile],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VolumeInsightTile);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  async function flushBoth(day: VolumeInsightResponse, week: VolumeInsightResponse) {
    request('DAY').flush(day);
    request('WEEK').flush(week);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('requests DAY and WEEK and shows loading until they answer', () => {
    expect(section('DAY').textContent).toContain('Loading');
    expect(section('WEEK').textContent).toContain('Loading');
    request('DAY');
    request('WEEK');
  });

  it('shows volume, change, date ranges and last-refreshed time', async () => {
    await flushBoth(insight({ periodType: 'DAY' }), insight());

    const week = section('WEEK');
    expect(week.textContent).toContain('Last week');
    expect(week.textContent).toContain('2026-10-05 – 2026-10-11');
    expect(week.querySelector('.volume')!.textContent).toContain('1,200');
    expect(week.querySelector('.change')!.textContent).toContain('+200');
    expect(week.querySelector('.change')!.textContent).toContain('+20.00%');
    expect(week.querySelector('.change')!.classList).toContain('up');
    expect(week.querySelector('.previous')!.textContent).toContain('1,000');
    expect(week.querySelector('.refreshed')!.textContent).toContain('Last refreshed');
    expect(section('DAY').textContent).toContain('Yesterday');
  });

  it('marks a drop as down with a minus sign', async () => {
    const drop = insight({
      change: { shareVolume: -300, shareVolumePercent: -25, grossAmount: -1, grossAmountPercent: -1 },
    });
    await flushBoth(insight({ periodType: 'DAY' }), drop);

    const change = section('WEEK').querySelector('.change')!;
    expect(change.classList).toContain('down');
    expect(change.textContent).toContain('-300');
    expect(change.textContent).toContain('-25.00%');
  });

  it('shows n/a when there is no previous period to compare with', async () => {
    const none = insight({
      previous: { startDate: '2026-09-28', endDate: '2026-10-04', tradeCount: 0, shareVolume: 0, grossAmount: 0 },
      change: { shareVolume: 1200, shareVolumePercent: null, grossAmount: 54000, grossAmountPercent: null },
    });
    await flushBoth(insight({ periodType: 'DAY' }), none);

    expect(section('WEEK').querySelector('.change')!.textContent).toContain('n/a');
  });

  it('shows zeros as flat, not an error', async () => {
    const zero = { startDate: '2026-10-11', endDate: '2026-10-11', tradeCount: 0, shareVolume: 0, grossAmount: 0 };
    const flat = insight({
      periodType: 'DAY',
      current: zero,
      previous: zero,
      change: { shareVolume: 0, shareVolumePercent: null, grossAmount: 0, grossAmountPercent: null },
    });
    await flushBoth(flat, insight());

    expect(section('DAY').querySelector('.change')!.classList).toContain('flat');
    expect(section('DAY').querySelector('.error')).toBeNull();
  });

  it('keeps one section when the other fails, and Refresh retries', async () => {
    request('DAY').flush('boom', { status: 500, statusText: 'Server Error' });
    request('WEEK').flush(insight());
    await fixture.whenStable();
    fixture.detectChanges();

    expect(section('DAY').querySelector('.error')).not.toBeNull();
    expect(section('DAY').textContent).not.toContain('boom');
    expect(section('WEEK').querySelector('.volume')!.textContent).toContain('1,200');

    (el().querySelector('.refresh') as HTMLButtonElement).click();
    await flushBoth(insight({ periodType: 'DAY' }), insight());

    expect(section('DAY').querySelector('.error')).toBeNull();
    expect(section('DAY').querySelector('.volume')).not.toBeNull();
  });
});
