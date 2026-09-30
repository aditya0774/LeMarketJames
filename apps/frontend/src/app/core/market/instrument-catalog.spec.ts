import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InstrumentCatalog } from './instrument-catalog';
import { TEST_INSTRUMENTS, loadTestInstruments } from './instrument-catalog.testing';

describe('InstrumentCatalog', () => {
  let catalog: InstrumentCatalog;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    catalog = TestBed.inject(InstrumentCatalog);
  });

  it('is empty until the list has loaded', () => {
    expect(catalog.all()).toEqual([]);
    expect(catalog.bySymbol('AAPL')).toBeUndefined();
  });

  it('loads the stock list from the API once', async () => {
    await loadTestInstruments();
    await catalog.load(); // cached: no second request

    TestBed.inject(HttpTestingController).verify();
    expect(catalog.all()).toEqual(TEST_INSTRUMENTS);
  });

  it('returns every stock for an empty query', async () => {
    await loadTestInstruments();
    expect(catalog.search('  ').length).toBe(catalog.all().length);
  });

  it('matches symbol or name case-insensitively', async () => {
    await loadTestInstruments();
    expect(catalog.search('tsl').map((i) => i.symbol)).toEqual(['TSLA']);
    expect(catalog.search('bronvidia').map((i) => i.symbol)).toEqual(['NVDA']);
  });

  it('returns nothing for an unknown query', async () => {
    await loadTestInstruments();
    expect(catalog.search('zzzz')).toEqual([]);
  });

  it('looks stocks up by symbol and by id', async () => {
    await loadTestInstruments();
    expect(catalog.bySymbol('aapl')?.instrumentId).toBe(1);
    expect(catalog.byId(5)?.symbol).toBe('TSLA');
    expect(catalog.byId(13)?.symbol).toBe('BRK-A');
    expect(catalog.bySymbol('NOPE')).toBeUndefined();
    expect(catalog.byId(999)).toBeUndefined();
  });

  it('keeps suspended stocks, marked not tradable', async () => {
    await loadTestInstruments();
    expect(catalog.bySymbol('CAVS')?.tradable).toBe(false);
  });

  it('can retry after a failed load', async () => {
    const failed = catalog.load();
    TestBed.inject(HttpTestingController).expectOne((r) => r.url.endsWith('/api/v1/instruments'))
      .flush('down', { status: 503, statusText: 'Service Unavailable' });
    await expect(failed).rejects.toBeTruthy();

    await loadTestInstruments();
    expect(catalog.all().length).toBe(TEST_INSTRUMENTS.length);
  });
});
