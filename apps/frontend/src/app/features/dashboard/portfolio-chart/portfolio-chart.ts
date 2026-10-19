import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { catchError, of, switchMap, tap } from 'rxjs';
import { PriceHistory } from '../../../core/history/price-history';
import { ChartPoint, ChartRange } from '../../../shared/models/chart.model';
import { PriceChart } from '../../../shared/components/price-chart/price-chart';

/**
 * The Overview's "Portfolio value" chart: what the user's stocks were worth over the chosen range.
 * Loads its own points, so the dashboard only says when the account changed (`refreshKey`).
 */
@Component({
  selector: 'app-portfolio-chart',
  imports: [PriceChart, RouterLink],
  template: `
    <section class="card pv" aria-label="Portfolio value chart">
      <app-price-chart
        heading="Portfolio value"
        [points]="points()"
        [range]="range()"
        [loading]="loading()"
        [error]="error()"
        emptyText="Once you own a stock, what your stocks are worth shows up here."
        (rangeChange)="range.set($event)"
      >
        <a empty-action class="btn btn-soft btn-sm" routerLink="/trade">Make your first trade</a>
      </app-price-chart>
    </section>
  `,
  styles: `
    .pv {
      display: flex;
      flex-direction: column;
      height: 100%;
      padding: 16px 18px 10px;
    }
    app-price-chart {
      flex: 1;
    }
  `,
})
export class PortfolioChart {
  /** Changes whenever the account may have changed (an order placed or filled), to reload. */
  readonly refreshKey = input(0);

  protected readonly range = signal<ChartRange>('1M');
  protected readonly points = signal<ChartPoint[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** The range the points on screen belong to. */
  private loadedRange: ChartRange | null = null;

  constructor() {
    const history = inject(PriceHistory);
    const request = computed(() => ({ range: this.range(), key: this.refreshKey() }));
    toObservable(request)
      .pipe(
        tap(({ range }) => {
          // A new range starts from a spinner; a refresh of the same range keeps the old line meanwhile.
          if (range !== this.loadedRange) {
            this.loading.set(true);
          }
        }),
        // switchMap drops a slower, older answer when the range changes again.
        switchMap(({ range }) =>
          history.portfolio(range).pipe(
            tap(() => (this.loadedRange = range)),
            catchError((err: unknown) => {
              this.error.set(
                err instanceof HttpErrorResponse && err.status === 503
                  ? 'Prices are temporarily unavailable.'
                  : 'Something went wrong. Try again in a moment.',
              );
              this.loading.set(false);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((points) => {
        if (points) {
          this.points.set(points);
          this.error.set(null);
          this.loading.set(false);
        }
      });
  }
}
