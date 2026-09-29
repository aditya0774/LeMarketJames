import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Instrument, InstrumentCatalog } from './instrument-catalog';

/**
 * A few stocks for specs, in the shape GET /api/v1/instruments returns. Not a copy of the real
 * market: specs only need a handful of known rows, including one suspended stock.
 */
export const TEST_INSTRUMENTS: readonly Instrument[] = [
  { instrumentId: 1, symbol: 'AAPL', name: 'BronApple', tradable: true },
  { instrumentId: 2, symbol: 'MSFT', name: 'Bronisoft', tradable: true },
  { instrumentId: 5, symbol: 'TSLA', name: 'TesLe', tradable: true },
  { instrumentId: 6, symbol: 'NVDA', name: 'BronVidia', tradable: true },
  { instrumentId: 13, symbol: 'BRK-A', name: 'Bronshire Hathaway', tradable: true },
  { instrumentId: 52, symbol: 'CAVS', name: 'Cavaliers Media Group', tradable: false },
];

/**
 * Loads {@link TEST_INSTRUMENTS} into the real InstrumentCatalog through its HTTP call. Needs
 * provideHttpClient() and provideHttpClientTesting() in the spec's providers.
 */
export async function loadTestInstruments(list: readonly Instrument[] = TEST_INSTRUMENTS): Promise<void> {
  const loaded = TestBed.inject(InstrumentCatalog).load();
  TestBed.inject(HttpTestingController)
    .expectOne((request) => request.url.endsWith('/api/v1/instruments'))
    .flush(list);
  await loaded;
}
