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
 * Every stock with its live price, day change and a trend graph. Presentational: the
 * Dashboard polls quotes and passes rows in; clicking a row asks it to open the trade popup.
 */
@Component({
  selector: 'app-market-list',
  imports: [CurrencyPipe, DecimalPipe, Sparkline],
  templateUrl: './market-list.html',
})
export class MarketList {
  private readonly catalog = inject(InstrumentCatalog);

  readonly rows = input<readonly MarketRow[]>([]);
  readonly selectSymbol = output<string>();

  protected readonly filter = signal('');

  // Reuses the catalog's matching rule so this filter and future search behave the same.
  protected readonly visibleRows = computed(() => {
    const matches = new Set(this.catalog.search(this.filter()).map((i) => i.symbol));
    return this.rows().filter((row) => matches.has(row.instrument.symbol));
  });
}
