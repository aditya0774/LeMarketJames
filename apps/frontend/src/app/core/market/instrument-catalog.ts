import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Instrument {
  instrumentId: number;
  symbol: string;
  name: string;
  tradable: boolean;
}

/**
 * Single source for "which stocks exist" on the frontend: powers stock search,
 * symbol lookup for orders (which only carry instrumentId) and symbol → instrumentId
 * mapping when placing orders.
 *
 * The list comes from `GET /api/v1/instruments`, i.e. the `instruments` table, so the frontend
 * never keeps its own copy (contract C5: the table is the supported stock list). It is loaded once
 * with {@link load}; until then every lookup sees an empty list. The list lives in a signal, so
 * computed views built from these methods update when it arrives.
 */
@Injectable({ providedIn: 'root' })
export class InstrumentCatalog {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/v1/instruments`;

  private readonly instruments = signal<readonly Instrument[]>([]);
  private loading: Promise<void> | null = null;

  /**
   * Fetches the list once; later calls reuse it. A failed load can be retried by calling again.
   */
  load(): Promise<void> {
    this.loading ??= firstValueFrom(this.http.get<Instrument[]>(this.url))
      .then((list) => this.instruments.set(list))
      .catch((err) => {
        this.loading = null;
        throw err;
      });
    return this.loading;
  }

  all(): readonly Instrument[] {
    return this.instruments();
  }

  /** Case-insensitive match on symbol or company name; an empty query returns everything. */
  search(query: string): Instrument[] {
    const q = query.trim().toLowerCase();
    if (!q) {
      return [...this.instruments()];
    }
    return this.instruments().filter(
      (i) => i.symbol.toLowerCase().includes(q) || i.name.toLowerCase().includes(q),
    );
  }

  bySymbol(symbol: string): Instrument | undefined {
    const s = symbol.trim().toUpperCase();
    return this.instruments().find((i) => i.symbol === s);
  }

  byId(instrumentId: number): Instrument | undefined {
    return this.instruments().find((i) => i.instrumentId === instrumentId);
  }
}
