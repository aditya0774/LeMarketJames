import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Auth } from '../auth/auth';
import { Watchlist } from './watchlist';

describe('Watchlist', () => {
  let watchlist: Watchlist;
  let http: HttpTestingController;
  const account = signal<number | null>(7);

  beforeEach(() => {
    account.set(7);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Auth, useValue: { currentAccountId: account } }],
    });
    watchlist = TestBed.inject(Watchlist);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function loadWith(symbols: string[]): Promise<void> {
    const loading = watchlist.load();
    http.expectOne('/api/v1/watchlist').flush({
      success: true,
      watchlist: symbols.map((symbol) => ({ symbol, addedAt: '2026-10-19T14:00:00Z' })),
    });
    await loading;
  }

  it('loads the watched stocks, newest first as the server sends them', async () => {
    await loadWith(['NVDA', 'TSLA']);

    expect(watchlist.status()).toBe('ready');
    expect(watchlist.symbols()).toEqual(['NVDA', 'TSLA']);
    expect(watchlist.isWatched('TSLA')).toBe(true);
  });

  it('is unavailable while the back end does not exist, and an error otherwise', async () => {
    let loading = watchlist.load();
    http.expectOne('/api/v1/watchlist').flush(null, { status: 404, statusText: 'Not Found' });
    await loading;
    expect(watchlist.status()).toBe('unavailable');

    loading = watchlist.load();
    http.expectOne('/api/v1/watchlist').flush(null, { status: 500, statusText: 'Server Error' });
    await loading;
    expect(watchlist.status()).toBe('error');
  });

  it('adds a stock at once and keeps it when the server agrees', async () => {
    await loadWith(['TSLA']);

    const adding = watchlist.add('AAPL');
    expect(watchlist.symbols()).toEqual(['AAPL', 'TSLA']);
    const request = http.expectOne('/api/v1/watchlist');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ symbol: 'AAPL' });
    request.flush({ success: true, item: { symbol: 'AAPL', addedAt: '' } }, { status: 201, statusText: 'Created' });
    await adding;

    expect(watchlist.symbols()).toEqual(['AAPL', 'TSLA']);
    expect(watchlist.changeError()).toBeNull();
  });

  it('takes an add back when the server refuses it, but not when it was already watched', async () => {
    await loadWith([]);

    let adding = watchlist.add('AAPL');
    http.expectOne('/api/v1/watchlist').flush(null, { status: 500, statusText: 'Server Error' });
    await adding;
    expect(watchlist.symbols()).toEqual([]);
    expect(watchlist.changeError()).toBe("Couldn't add AAPL to your watchlist. Try again in a moment.");

    adding = watchlist.add('AAPL');
    http.expectOne('/api/v1/watchlist').flush({ success: false, code: 'ALREADY_WATCHED' }, { status: 409, statusText: 'Conflict' });
    await adding;
    expect(watchlist.symbols()).toEqual(['AAPL']);
  });

  it('removes a stock at once, and puts it back in its place if the server refuses', async () => {
    await loadWith(['NVDA', 'TSLA', 'AAPL']);

    const removing = watchlist.toggle('TSLA');
    expect(watchlist.symbols()).toEqual(['NVDA', 'AAPL']);
    const request = http.expectOne('/api/v1/watchlist/TSLA');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 500, statusText: 'Server Error' });
    await removing;

    expect(watchlist.symbols()).toEqual(['NVDA', 'TSLA', 'AAPL']);
    expect(watchlist.changeError()).toContain("Couldn't remove TSLA");
  });

  it("forgets one account's watchlist when another signs in", async () => {
    await loadWith(['TSLA']);

    account.set(8);
    TestBed.tick();

    expect(watchlist.symbols()).toEqual([]);
    expect(watchlist.status()).toBe('loading');
  });
});
