import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MostActiveStocksResponse } from '../../../shared/models/most-active-stocks.model';
import { MostActiveStocksService } from '../../../shared/services/most-active-stocks.service';

export const MOST_ACTIVE_STOCKS_REFRESH_MS = 30_000;

@Component({
  selector: 'staff-most-active-stocks-tile',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './most-active-stocks-tile.html',
  styleUrls: ['./most-active-stocks-tile.scss'],
})
export class MostActiveStocksTile {
  private readonly service = inject(MostActiveStocksService);
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