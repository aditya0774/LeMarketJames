import { Injectable } from '@angular/core';

export interface Instrument {
  instrumentId: number;
  symbol: string;
  name: string;
  tradable: boolean;
}

/**
 * Mirrors the instruments seeded by database/schema 001 + 006 (GOOGL made non-tradable by 005).
 *
 * TEMPORARY: the backend has no instrument listing endpoint yet. Once
 * `GET /api/v1/instruments` ships (see API-CONTRACTS.md, "Dashboard & Trade"), load this
 * list from the API instead. Consumers only use the InstrumentCatalog methods, so the
 * swap stays inside this file.
 */
const SEEDED_INSTRUMENTS: readonly Instrument[] = [
  { instrumentId: 1, symbol: 'AAPL', name: 'Apple Inc', tradable: true },
  { instrumentId: 2, symbol: 'MSFT', name: 'Microsoft Corp', tradable: true },
  { instrumentId: 3, symbol: 'GOOGL', name: 'Alphabet Inc', tradable: false },
  { instrumentId: 4, symbol: 'AMZN', name: 'Amazon.com Inc', tradable: true },
  { instrumentId: 5, symbol: 'TSLA', name: 'Tesla Inc', tradable: true },
  { instrumentId: 6, symbol: 'NVDA', name: 'NVIDIA Corp', tradable: true },
];

/**
 * Single source for "which stocks exist" on the frontend: powers stock search,
 * symbol lookup for orders (which only carry instrumentId) and symbol → instrumentId
 * mapping when placing orders.
 */
@Injectable({ providedIn: 'root' })
export class InstrumentCatalog {
  all(): readonly Instrument[] {
    return SEEDED_INSTRUMENTS;
  }

  /** Case-insensitive match on symbol or company name; an empty query returns everything. */
  search(query: string): Instrument[] {
    const q = query.trim().toLowerCase();
    if (!q) {
      return [...SEEDED_INSTRUMENTS];
    }
    return SEEDED_INSTRUMENTS.filter(
      (i) => i.symbol.toLowerCase().includes(q) || i.name.toLowerCase().includes(q),
    );
  }

  bySymbol(symbol: string): Instrument | undefined {
    const s = symbol.trim().toUpperCase();
    return SEEDED_INSTRUMENTS.find((i) => i.symbol === s);
  }

  byId(instrumentId: number): Instrument | undefined {
    return SEEDED_INSTRUMENTS.find((i) => i.instrumentId === instrumentId);
  }
}
