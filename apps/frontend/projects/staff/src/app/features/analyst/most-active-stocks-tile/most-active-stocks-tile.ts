import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MostActiveStocksResponse } from '../../../shared/models/most-active-stocks.model';
import { MostActiveStocksService } from '../../../shared/services/most-active-stocks.service';
import { changeText, InsightFlags } from '../insight-flags/insight-flags';
import { InsightFlagChip } from '../insight-flags/insight-flag-chip';

export const MOST_ACTIVE_STOCKS_REFRESH_MS = 30_000;

@Component({
  selector: 'staff-most-active-stocks-tile',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, MatButtonModule, MatCardModule, MatIconModule, InsightFlagChip],
  templateUrl: './most-active-stocks-tile.html',
  styleUrls: ['./most-active-stocks-tile.scss'],
})
export class MostActiveStocksTile {
  private readonly service = inject(MostActiveStocksService);
  /** Where each stock's flag goes for the "What changed" strip; absent outside the dashboard. */
  private readonly flags = inject(InsightFlags, { optional: true });
  protected readonly changeText = changeText;
  readonly data = signal<MostActiveStocksResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);

  constructor() {
    this.refresh();
    interval(MOST_ACTIVE_STOCKS_REFRESH_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.refresh(true));
  }

  refresh(silent = false): void {
    if (!silent) {
      this.loading.set(true);
      this.error.set(false);
    }
    this.service.get().subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
        this.error.set(false);
        this.flags?.report('most-active-stocks', data.stocks.map((stock) => ({
          key: `stock-${stock.symbol}`,
          label: `${stock.symbol} traded value, this week so far`,
          comparedWith: `vs ${data.previousStartDate} – ${data.previousEndDate}`,
          flag: stock.flag,
        })));
      },
      error: () => {
        if (!silent) {
          this.loading.set(false);
          this.error.set(true);
        }
      },
    });
  }
}