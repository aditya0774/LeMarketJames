import { Component, computed, inject, input, output } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HoldingDto } from '../../../shared/models/holdings.model';
import { InstrumentCatalog } from '../../../core/market/instrument-catalog';
import { SignedCurrencyPipe } from '../../../shared/pipes/signed-currency.pipe';

export interface PortfolioTotals {
  value: number;
  cost: number;
  gainLoss: number;
  /** Gain/loss relative to cost basis; 0 when nothing is held. */
  gainLossPercent: number;
}

/** Sums the per-holding figures the backend already computes. Shared with the stat strip. */
export function portfolioTotals(holdings: readonly HoldingDto[]): PortfolioTotals {
  const value = holdings.reduce((sum, h) => sum + h.currentValue, 0);
  const cost = holdings.reduce((sum, h) => sum + h.totalCost, 0);
  const gainLoss = value - cost;
  return { value, cost, gainLoss, gainLossPercent: cost ? (gainLoss / cost) * 100 : 0 };
}

/** Holdings table (mockup "Portfolio" panel). Rows open the trade page for that stock. */
@Component({
  selector: 'app-portfolio-panel',
  imports: [CurrencyPipe, DecimalPipe, SignedCurrencyPipe],
  templateUrl: './portfolio-panel.html',
})
export class PortfolioPanel {
  private readonly catalog = inject(InstrumentCatalog);

  readonly holdings = input<readonly HoldingDto[]>([]);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly selectSymbol = output<string>();

  protected readonly totals = computed(() => portfolioTotals(this.holdings()));

  protected nameFor(symbol: string): string {
    return this.catalog.bySymbol(symbol)?.name ?? '';
  }
}
