import { InstrumentCatalog } from './instrument-catalog';

describe('InstrumentCatalog', () => {
  const catalog = new InstrumentCatalog();

  it('returns every instrument for an empty query', () => {
    expect(catalog.search('  ').length).toBe(catalog.all().length);
  });

  it('matches symbol and company name case-insensitively', () => {
    expect(catalog.search('tsl').map((i) => i.symbol)).toEqual(['TSLA']);
    expect(catalog.search('bronvidia').map((i) => i.symbol)).toEqual(['NVDA']);
  });

  it('returns nothing for an unknown query', () => {
    expect(catalog.search('zzzz')).toEqual([]);
  });

  it('looks instruments up by symbol and by id, matching the DB seed ids', () => {
    expect(catalog.bySymbol('aapl')?.instrumentId).toBe(1);
    expect(catalog.byId(5)?.symbol).toBe('TSLA');
    expect(catalog.byId(13)?.symbol).toBe('BRK-A');
    expect(catalog.byId(51)?.symbol).toBe('DE');
    // Id 7 was LMT, removed by migration 009; ids are never reused.
    expect(catalog.byId(7)).toBeUndefined();
    expect(catalog.bySymbol('NOPE')).toBeUndefined();
    expect(catalog.byId(999)).toBeUndefined();
  });

  it('lists the 50-stock market, all tradable (migration 009)', () => {
    expect(catalog.all().length).toBe(50);
    expect(new Set(catalog.all().map((i) => i.symbol)).size).toBe(50);
    expect(catalog.all().every((i) => i.tradable)).toBe(true);
    expect(catalog.bySymbol('LMT')).toBeUndefined();
  });
});
