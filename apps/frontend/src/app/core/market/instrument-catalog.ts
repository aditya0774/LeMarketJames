import { Injectable } from '@angular/core';

export interface Instrument {
  instrumentId: number;
  symbol: string;
  name: string;
  tradable: boolean;
}

/**
 * Mirrors the 50-stock market seeded by database/schema 009 (001/006/007 created the first rows;
 * 009 removed LMT, gave every company its final LeBronified name and made every stock tradable).
 * Ids must match the database: 009 pins them explicitly, and id 7 (the old LMT) is intentionally unused.
 *
 * TEMPORARY: the backend has no instrument listing endpoint yet. Once
 * `GET /api/v1/instruments` ships (see API-CONTRACTS.md, "Dashboard & Trade"), load this
 * list from the API instead. Consumers only use the InstrumentCatalog methods, so the
 * swap stays inside this file.
 */
const SEEDED_INSTRUMENTS: readonly Instrument[] = [
  { instrumentId: 1, symbol: 'AAPL', name: 'BronApple', tradable: true },
  { instrumentId: 2, symbol: 'MSFT', name: 'Bronisoft', tradable: true },
  { instrumentId: 3, symbol: 'GOOGL', name: 'AlphaBron', tradable: true },
  { instrumentId: 4, symbol: 'AMZN', name: 'AkronZon', tradable: true },
  { instrumentId: 5, symbol: 'TSLA', name: 'TesLe', tradable: true },
  { instrumentId: 6, symbol: 'NVDA', name: 'BronVidia', tradable: true },
  { instrumentId: 8, symbol: 'AVGO', name: 'Broncom', tradable: true },
  { instrumentId: 9, symbol: 'TSM', name: 'Taiwan SemiBronductor', tradable: true },
  { instrumentId: 10, symbol: 'META', name: 'MetaBron Platforms', tradable: true },
  { instrumentId: 11, symbol: 'MU', name: 'MicBron Technology', tradable: true },
  { instrumentId: 12, symbol: 'LLY', name: 'eLe Bronny', tradable: true },
  { instrumentId: 13, symbol: 'BRK-A', name: 'Bronshire Hathaway', tradable: true },
  { instrumentId: 14, symbol: 'AMD', name: 'Advanced Micro Bronvices', tradable: true },
  { instrumentId: 15, symbol: 'JPM', name: 'JPBron Chase', tradable: true },
  { instrumentId: 16, symbol: 'WMT', name: 'BronMart', tradable: true },
  { instrumentId: 17, symbol: 'V', name: 'VisaBron', tradable: true },
  { instrumentId: 18, symbol: 'ASML', name: 'ASBron Holding', tradable: true },
  { instrumentId: 19, symbol: 'XOM', name: 'Exxon MoBron', tradable: true },
  { instrumentId: 20, symbol: 'JNJ', name: 'Bronson & Bronson', tradable: true },
  { instrumentId: 21, symbol: 'INTC', name: 'BronTel', tradable: true },
  { instrumentId: 22, symbol: 'MA', name: 'MasterBronCard', tradable: true },
  { instrumentId: 23, symbol: 'ABBV', name: 'BronVie', tradable: true },
  { instrumentId: 24, symbol: 'ORCL', name: 'Bronacle', tradable: true },
  { instrumentId: 25, symbol: 'PLTR', name: 'PalanBron', tradable: true },
  { instrumentId: 26, symbol: 'CSCO', name: 'BronCisco Systems', tradable: true },
  { instrumentId: 27, symbol: 'CVX', name: 'CheBron', tradable: true },
  { instrumentId: 28, symbol: 'COST', name: 'CostBronco', tradable: true },
  { instrumentId: 29, symbol: 'BAC', name: 'Bank of Akron', tradable: true },
  { instrumentId: 30, symbol: 'LRCX', name: 'LamBron Research', tradable: true },
  { instrumentId: 31, symbol: 'KO', name: 'Coca-Bronla', tradable: true },
  { instrumentId: 32, symbol: 'AMAT', name: 'Applied Bronterials', tradable: true },
  { instrumentId: 33, symbol: 'CAT', name: 'CaterBron', tradable: true },
  { instrumentId: 34, symbol: 'MRK', name: 'Merck & Bron', tradable: true },
  { instrumentId: 35, symbol: 'DELL', name: 'BronDell Technologies', tradable: true },
  { instrumentId: 36, symbol: 'PG', name: 'Bronter & Gamble', tradable: true },
  { instrumentId: 37, symbol: 'IBM', name: 'International Bronsiness Machines', tradable: true },
  { instrumentId: 38, symbol: 'AMGN', name: 'AmBron', tradable: true },
  { instrumentId: 39, symbol: 'BHP', name: 'BronHP Group', tradable: true },
  { instrumentId: 40, symbol: 'LIN', name: 'LindBron', tradable: true },
  { instrumentId: 41, symbol: 'SAN', name: 'Banco SantanBron', tradable: true },
  { instrumentId: 42, symbol: 'QCOM', name: 'QualBroncomm', tradable: true },
  { instrumentId: 43, symbol: 'TD', name: 'Toronto-Bronminion Bank', tradable: true },
  { instrumentId: 44, symbol: 'STX', name: 'SeaBron Technology', tradable: true },
  { instrumentId: 45, symbol: 'AXP', name: 'AmeriBron Express', tradable: true },
  { instrumentId: 46, symbol: 'APH', name: 'AmphenBron', tradable: true },
  { instrumentId: 47, symbol: 'TTE', name: 'TotalBronergies', tradable: true },
  { instrumentId: 48, symbol: 'CRM', name: 'Bronforce', tradable: true },
  { instrumentId: 49, symbol: 'VZ', name: 'VeriBron', tradable: true },
  { instrumentId: 50, symbol: 'SHOP', name: 'ShopiBron', tradable: true },
  { instrumentId: 51, symbol: 'DE', name: 'Deere & Bronpany', tradable: true },
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
