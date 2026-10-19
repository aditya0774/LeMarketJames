import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Instrument, InstrumentCatalog } from '../../../core/market/instrument-catalog';
import { Quote } from '../../../shared/models/quote.model';
import { Sparkline } from '../../../shared/components/sparkline/sparkline';

export interface MarketRow {
  instrument: Instrument;
  quote: Quote | null;
  /** Prices seen so far, oldest first; starts at today's open. */
  history: readonly number[];
}

/**
 * The redesign's "All stocks" table: every stock with its live price, day change and a trend
 * graph, a star to watch it, and a search box. Presentational: the Market page polls quotes and
 * passes rows in; clicking a row asks it to open the trade for that stock, and the star asks it to
 * add or remove the stock from the watchlist.
 */
@Component({
  selector: 'app-market-list',
  imports: [CurrencyPipe, DecimalPipe, Sparkline],
  templateUrl: './market-list.html',
  styleUrl: './market-list.css',
})
export class MarketList {
  private readonly catalog = inject(InstrumentCatalog);

  readonly rows = input<readonly MarketRow[]>([]);
  /** The watched symbols, or null to show no stars (the watchlist is loading or not available). */
  readonly watched = input<ReadonlySet<string> | null>(null);
  readonly selectSymbol = output<string>();
  readonly toggleWatch = output<string>();

  protected readonly filter = signal('');

  // Reuses the catalog's matching rule so this filter and future search behave the same.
  protected readonly visibleRows = computed(() => {
    const matches = new Set(this.catalog.search(this.filter()).map((i) => i.symbol));
    return this.rows().filter((row) => matches.has(row.instrument.symbol));
  });
}
