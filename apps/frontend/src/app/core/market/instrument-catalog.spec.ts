import { InstrumentCatalog } from './instrument-catalog';

describe('InstrumentCatalog', () => {
  const catalog = new InstrumentCatalog();

  it('returns every instrument for an empty query', () => {
    expect(catalog.search('  ').length).toBe(catalog.all().length);
  });

  it('matches symbol and company name case-insensitively', () => {
    expect(catalog.search('tsl').map((i) => i.symbol)).toEqual(['TSLA']);
    expect(catalog.search('nvidibron').map((i) => i.symbol)).toEqual(['NVDA']);
  });

  it('returns nothing for an unknown query', () => {
    expect(catalog.search('zzzz')).toEqual([]);
  });

  it('looks instruments up by symbol and by id, matching the DB seed ids', () => {
    expect(catalog.bySymbol('aapl')?.instrumentId).toBe(1);
    expect(catalog.byId(5)?.symbol).toBe('TSLA');
    expect(catalog.bySymbol('NOPE')).toBeUndefined();
    expect(catalog.byId(999)).toBeUndefined();
  });

  it('marks GOOGL as not tradable (migration 005)', () => {
    expect(catalog.bySymbol('GOOGL')?.tradable).toBe(false);
  });
});
