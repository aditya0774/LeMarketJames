import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { loadTestInstruments } from '../../../core/market/instrument-catalog.testing';
import { SimulatorPage } from './simulator-page';

/**
 * Weekdays from Monday 2026-02-02 to Monday 2026-05-11 (71 days), priced 100, 101, 102 ...: the
 * price on day n is 100 + n, so every expected figure below is easy to check by hand.
 *   - 2026-02-11 (the default, three months back) is day 7, $107; 2026-04-13 is day 50, $150.
 *   - The last day, 2026-05-11, is day 70, the latest price of $170.
 */
function history(): { time: string; price: number }[] {
  const points: { time: string; price: number }[] = [];
  for (let day = Date.parse('2026-02-02T20:00:00Z'); day <= Date.parse('2026-05-11T20:00:00Z'); day += 86_400_000) {
    const weekday = new Date(day).getUTCDay();
    if (weekday !== 0 && weekday !== 6) {
      points.push({ time: new Date(day).toISOString(), price: 100 + points.length });
    }
  }
  return points;
}

describe('SimulatorPage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const el = () => harness.fixture.nativeElement as HTMLElement;
  const text = () => el().textContent!.replace(/\s+/g, ' ').trim();

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'learn/simulator', component: SimulatorPage }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Opens the page and answers the stock list; the price request comes once a stock is known. */
  async function open(url = '/learn/simulator') {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, SimulatorPage);
    await loadTestInstruments();
    harness.detectChanges();
  }

  function historyRequest(symbol: string) {
    return http.expectOne(
      (r) => r.url.endsWith(`/api/quotes/${symbol}/history`) && r.params.get('range') === '6M',
    );
  }

  function answer(symbol: string) {
    historyRequest(symbol).flush({ success: true, symbol, range: '6M', points: history() });
    harness.detectChanges();
  }

  function type(selector: string, value: string, event = 'input') {
    const input = el().querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event(event));
    harness.detectChanges();
  }

  it('asks for six months of the first stock, and says it is loading', async () => {
    await open();

    expect(text()).toContain('Loading prices');
    answer('AAPL');
    expect(text()).not.toContain('Loading prices');
  });

  it('asks for the stock named in the URL, in any case', async () => {
    await open('/learn/simulator?symbol=msft');

    answer('MSFT');
    expect(el().querySelector<HTMLSelectElement>('select')!.value).toBe('MSFT');
  });

  it('falls back to the first stock for a symbol that does not exist', async () => {
    await open('/learn/simulator?symbol=NOPE');

    answer('AAPL');
    expect(el().querySelector<HTMLSelectElement>('select')!.value).toBe('AAPL');
  });

  it('defaults to $1,000 three months back and shows what it would be worth', async () => {
    await open();
    answer('AAPL');

    const date = el().querySelector<HTMLInputElement>('input[type="date"]')!;
    expect(date.value).toBe('2026-02-11');
    expect(text()).toContain('9.3458 shares of BronApple at $107.00 on Feb 11, 2026');
    expect(text()).toContain('Worth today$1,588.79');
    expect(text()).toContain('Theoretical gain+$588.79▲ +58.88%');
  });

  it('limits the date box to the days the prices cover', async () => {
    await open();
    answer('AAPL');

    const date = el().querySelector<HTMLInputElement>('input[type="date"]')!;
    expect(date.min).toBe('2026-02-02');
    expect(date.max).toBe('2026-05-11');
    expect(text()).toContain('Prices go back to 2026-02-02');
  });

  it('updates the result when the amount changes, without asking for prices again', async () => {
    await open();
    answer('AAPL');

    type('input[type="number"]', '2000');

    expect(text()).toContain('Theoretical gain+$1,177.57▲ +58.88%');
  });

  it('updates the result when the day changes', async () => {
    await open();
    answer('AAPL');

    type('input[type="date"]', '2026-04-13', 'change');

    expect(text()).toContain('at $150.00');
    expect(text()).toContain('Worth today$1,133.33');
    expect(text()).toContain('Theoretical gain+$133.33▲ +13.33%');
  });

  it('buys on the next trading day when the day picked was a weekend, and says so', async () => {
    await open();
    answer('AAPL');

    type('input[type="date"]', '2026-04-11', 'change');

    expect(text()).toContain('There was no trading on 2026-04-11, so the next trading day, 2026-04-13, is used.');
    expect(text()).toContain('at $150.00');
  });

  it('keeps a day outside the prices inside them', async () => {
    await open();
    answer('AAPL');

    type('input[type="date"]', '2019-01-01', 'change');
    expect(el().querySelector<HTMLInputElement>('input[type="date"]')!.value).toBe('2026-02-02');

    type('input[type="date"]', '2030-01-01', 'change');
    expect(el().querySelector<HTMLInputElement>('input[type="date"]')!.value).toBe('2026-05-11');
  });

  it('sets the day from the quick picks', async () => {
    await open();
    answer('AAPL');

    const pick = Array.from(el().querySelectorAll<HTMLButtonElement>('.sim-picks .chip')).find(
      (b) => b.textContent!.trim() === '1 month ago',
    )!;
    pick.click();
    harness.detectChanges();

    expect(el().querySelector<HTMLInputElement>('input[type="date"]')!.value).toBe('2026-04-11');
    expect(text()).toContain('Theoretical gain+$133.33▲ +13.33%');
  });

  it('shows no change, and no chart, for a purchase on the latest day', async () => {
    await open();
    answer('AAPL');

    type('input[type="date"]', '2026-05-11', 'change');

    expect(text()).toContain('Theoretical gain+$0.00▲ +0.00%');
    expect(text()).toContain('No time has passed');
    expect(el().querySelector('app-price-chart')).toBeNull();
  });

  it('draws the investment since the purchase on a chart without the range switch', async () => {
    await open();
    answer('AAPL');

    expect(el().querySelector('app-price-chart h2')?.textContent).toBe('Your investment');
    expect(el().querySelector('.pc-ranges')).toBeNull();
    expect(el().querySelector('.pc-change')?.textContent).toContain('since you bought');
  });

  it('asks for an amount above zero instead of a result', async () => {
    await open();
    answer('AAPL');

    for (const bad of ['', '0', '-5']) {
      type('input[type="number"]', bad);

      expect(text(), `amount "${bad}"`).toContain('Enter an amount above $0.');
      expect(text()).toContain('Enter an amount to see the result.');
      expect(text()).not.toContain('Theoretical');
    }
  });

  it('loads the new stock when another is chosen', async () => {
    await open();
    answer('AAPL');

    type('select', 'TSLA', 'change');
    await harness.fixture.whenStable();
    harness.detectChanges();

    answer('TSLA');
    expect(text()).toContain('shares of TesLe at');
  });

  it('says prices are unavailable, and tries again when asked', async () => {
    await open();

    historyRequest('AAPL').flush('down', { status: 503, statusText: 'Service Unavailable' });
    harness.detectChanges();
    expect(text()).toContain('Prices are temporarily unavailable');

    el().querySelector<HTMLButtonElement>('.sim-out button')!.click();
    harness.detectChanges();
    answer('AAPL');

    expect(text()).toContain('Theoretical gain');
    expect(text()).not.toContain('temporarily unavailable');
  });

  it('says it is not a real trade and links back to Learn', async () => {
    await open();
    answer('AAPL');

    expect(text()).toContain("It doesn't change your balance or holdings.");
    expect(el().querySelector('.sim-back')?.getAttribute('href')).toBe('/learn');
  });
});
